package com.bbss.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtAuthenticationFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ATTRIBUTE_TENANT_ID = "tenantId";
    private static final String ATTRIBUTE_USER_ID = "userId";
    private static final String ATTRIBUTE_ROLES = "roles";
    private static final String HEADER_TENANT_ID = "X-Tenant-ID";
    private static final String HEADER_USER_ID = "X-User-ID";
    private static final String HEADER_ROLES = "X-User-Roles";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final List<String> excludedPaths = List.of(
        "/api/v1/auth/**",
        "/actuator/health",
        "/actuator/info",
        "/fallback"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip JWT validation for excluded paths
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return writeUnauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = validateAndExtractClaims(token);

            String tenantId = claims.get("tenantId", String.class);
            String userId = claims.getSubject();
            List<String> roles = extractRoles(claims);

            // Mutate the request to propagate tenant/user info to downstream services
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER_TENANT_ID, tenantId != null ? tenantId : "")
                .header(HEADER_USER_ID, userId != null ? userId : "")
                .header(HEADER_ROLES, String.join(",", roles))
                .build();

            ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

            // Store in exchange attributes for downstream gateway filters
            mutatedExchange.getAttributes().put(ATTRIBUTE_TENANT_ID, tenantId);
            mutatedExchange.getAttributes().put(ATTRIBUTE_USER_ID, userId);
            mutatedExchange.getAttributes().put(ATTRIBUTE_ROLES, roles);

            // Build Spring Security authentication object
            List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);

            log.debug("JWT validated successfully for user: {}, tenant: {}", userId, tenantId);

            return chain.filter(mutatedExchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            return writeUnauthorizedResponse(exchange, "Token has expired");
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return writeUnauthorizedResponse(exchange, "Invalid or expired token");
        } catch (Exception e) {
            log.error("Unexpected error during JWT validation", e);
            return writeUnauthorizedResponse(exchange, "Authentication error");
        }
    }

    private Claims validateAndExtractClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(
            Base64.getDecoder().decode(jwtSecret)
        );
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Claims claims) {
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?> rolesList) {
            return rolesList.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private boolean isExcludedPath(String path) {
        return excludedPaths.stream()
            .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> writeUnauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
            "{\"error\":\"Unauthorized\",\"message\":\"%s\"}",
            message
        );

        DataBuffer buffer = response.bufferFactory()
            .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}
