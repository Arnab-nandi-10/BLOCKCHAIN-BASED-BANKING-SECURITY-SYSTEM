package com.bbss.audit.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.bbss.shared.dto.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * JWT authentication filter for the audit-service.
 *
 * <p>Executed once per request (extends {@link OncePerRequestFilter}).
 * Processing steps:
 * <ol>
 *   <li>Extract the Bearer token from the {@code Authorization} header.</li>
 *   <li>Validate the JWT signature against the shared HMAC-SHA256 secret.</li>
 *   <li>Extract the subject (user ID or service name) and the {@code roles}
 *       and {@code tenantId} claims.</li>
 *   <li>Populate {@link SecurityContextHolder} with a
 *       {@link UsernamePasswordAuthenticationToken} carrying the parsed
 *       granted authorities.</li>
 *   <li>Set the tenant identifier on {@link TenantContext} so that downstream
 *       components can access it without explicit method parameter threading.</li>
 *   <li>Clear {@link TenantContext} in the {@code finally} block to prevent
 *       thread-pool leakage.</li>
 * </ol>
 *
 * <p>Any JWT validation failure (expired, malformed, wrong signature) causes
 * the filter to clear the security context and pass the request downstream
 * unauthenticated.  Spring Security's authorization rules will then reject the
 * request with HTTP 401.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX         = "Bearer ";
    private static final String CLAIM_TENANT_ID       = "tenantId";
    private static final String CLAIM_ROLES           = "roles";
    private static final String ROLE_PREFIX           = "ROLE_";

    private final JwtConfig jwtConfig;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain)
            throws ServletException, IOException {

        try {
            String token = extractToken(request);

            if (StringUtils.hasText(token)) {
                Claims claims = validateAndParseClaims(token);

                if (claims != null) {
                    String subject  = claims.getSubject();
                    String tenantId = claims.get(CLAIM_TENANT_ID, String.class);

                    Collection<SimpleGrantedAuthority> authorities = extractAuthorities(claims);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(subject, null, authorities);
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    if (StringUtils.hasText(tenantId)) {
                        TenantContext.setTenantId(tenantId);
                    }

                    log.debug("JWT authenticated: subject={} tenantId={} roles={}",
                            subject, tenantId, authorities);
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            // Always clear thread-local state to prevent leakage across requests
            // reusing the same thread from the container pool.
            TenantContext.clear();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Extracts the Bearer token from the {@code Authorization} header.
     *
     * @return the raw JWT string, or {@code null} if the header is absent or
     *         does not start with {@code "Bearer "}
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    /**
     * Validates the JWT signature and returns the parsed claims.
     *
     * @param token the raw JWT string
     * @return the {@link Claims} payload, or {@code null} if validation fails
     */
    private Claims validateAndParseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(buildSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Parses the {@code roles} claim from the JWT payload and converts each
     * role string into a {@link SimpleGrantedAuthority} with the
     * {@code ROLE_} prefix.
     *
     * <p>The {@code roles} claim may be a {@code List<String>} (standard) or
     * a comma-separated {@code String} (legacy issuers).
     *
     * @param claims the validated JWT payload
     * @return a non-null (possibly empty) collection of granted authorities
     */
    @SuppressWarnings("unchecked")
    private Collection<SimpleGrantedAuthority> extractAuthorities(Claims claims) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        Object rolesClaim = claims.get(CLAIM_ROLES);
        if (rolesClaim == null) {
            return authorities;
        }

        if (rolesClaim instanceof List<?> roleList) {
            for (Object role : roleList) {
                String roleStr = role.toString();
                String authority = roleStr.startsWith(ROLE_PREFIX)
                        ? roleStr
                        : ROLE_PREFIX + roleStr;
                authorities.add(new SimpleGrantedAuthority(authority));
            }
        } else if (rolesClaim instanceof String rolesStr) {
            for (String role : rolesStr.split(",")) {
                String trimmed  = role.trim();
                String authority = trimmed.startsWith(ROLE_PREFIX)
                        ? trimmed
                        : ROLE_PREFIX + trimmed;
                authorities.add(new SimpleGrantedAuthority(authority));
            }
        }

        return authorities;
    }

    /**
     * Builds the {@link SecretKey} used to verify incoming JWT signatures.
     *
     * <p>The key material is Base64-decoded from {@link JwtConfig#getSecret()}.
     */
    private SecretKey buildSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtConfig.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
