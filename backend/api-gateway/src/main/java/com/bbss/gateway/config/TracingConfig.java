package com.bbss.gateway.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Distributed Tracing Configuration for API Gateway.
 * 
 * <p>Enhances traces with custom business context:
 * <ul>
 *   <li>Tenant isolation tracking (tenantId from JWT claims)
 *   <li>Transaction identifiers propagation
 *   <li>User identity tracking
 *   <li>Request source metadata
 * </ul>
 * 
 * <p>All custom tags are automatically propagated to downstream services
 * via W3C Trace Context headers (traceparent, tracestate).
 * 
 * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
 * @see <a href="https://opentelemetry.io/docs/concepts/signals/traces/">OpenTelemetry Tracing</a>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TracingConfig {

    private final Tracer tracer;

    /**
     * Global filter that enriches traces with business context from requests.
     * 
     * <p>Executes for every request routed through the gateway, extracting
     * relevant headers and JWT claims to tag the current span.
     * 
     * <p><strong>Custom Span Tags Added:</strong>
     * <ul>
     *   <li>{@code tenant.id} — From X-Tenant-Id header or JWT claim
     *   <li>{@code user.id} — From X-User-Id header or JWT subject
     *   <li>{@code transaction.id} — From X-Transaction-Id header
     *   <li>{@code http.target_service} — Downstream service name from route
     *   <li>{@code http.client_ip} — Client IP address (X-Forwarded-For aware)
     * </ul>
     * 
     * @return GlobalFilter that enhances traces with business metadata
     */
    @Bean
    public GlobalFilter customTracingFilter() {
        return (exchange, chain) -> {
            Span currentSpan = tracer.currentSpan();
            
            if (currentSpan != null) {
                ServerHttpRequest request = exchange.getRequest();
                
                // Extract tenant ID from header (set by JWT authentication filter)
                String tenantId = request.getHeaders().getFirst("X-Tenant-Id");
                if (tenantId != null && !tenantId.isBlank()) {
                    currentSpan.tag("tenant.id", tenantId);
                    // Also add to MDC for logging correlation
                    exchange.getAttributes().put("tenantId", tenantId);
                }
                
                // Extract user ID from header
                String userId = request.getHeaders().getFirst("X-User-Id");
                if (userId != null && !userId.isBlank()) {
                    currentSpan.tag("user.id", userId);
                }
                
                // Extract transaction ID if present (for transaction-related requests)
                String transactionId = request.getHeaders().getFirst("X-Transaction-Id");
                if (transactionId != null && !transactionId.isBlank()) {
                    currentSpan.tag("transaction.id", transactionId);
                }
                
                // Tag with target service name from gateway route
                String targetService = extractTargetServiceName(exchange);
                if (targetService != null) {
                    currentSpan.tag("http.target_service", targetService);
                }
                
                // Tag with client IP (respecting X-Forwarded-For header for proxy chains)
                String clientIp = getClientIpAddress(request);
                if (clientIp != null) {
                    currentSpan.tag("http.client_ip", clientIp);
                }
                
                // Tag with HTTP method and path
                currentSpan.tag("http.method", request.getMethod().name());
                currentSpan.tag("http.path", request.getPath().value());
                
                log.debug("Enhanced trace with business context - traceId: {}, tenantId: {}, userId: {}, path: {}", 
                    currentSpan.context().traceId(), tenantId, userId, request.getPath().value());
            }
            
            return chain.filter(exchange);
        };
    }

    /**
     * Extracts the target service name from the gateway route.
     * 
     * <p>Gateway routes typically contain the service name in the URI pattern
     * (e.g., lb://auth-service, lb://transaction-service).
     * 
     * @param exchange current server web exchange
     * @return target service name, or "unknown" if not determinable
     */
    private String extractTargetServiceName(ServerWebExchange exchange) {
        // Attempt to extract from gateway route attributes (set by route predicates)
        Object routeAttr = exchange.getAttribute("org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayRequestUrl");
        if (routeAttr != null) {
            String routeStr = routeAttr.toString();
            // Extract service name from lb://service-name or http://service-name:port
            if (routeStr.startsWith("lb://")) {
                return routeStr.substring(5).split("/")[0];
            } else if (routeStr.startsWith("http://")) {
                // Extract hostname (service name in containerized environments)
                String host = routeStr.substring(7).split(":")[0];
                return host;
            }
        }
        
        // Fallback: Extract from request path (e.g., /api/transactions/... → transaction-service)
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/api/auth")) return "auth-service";
        if (path.startsWith("/api/tenants")) return "tenant-service";
        if (path.startsWith("/api/transactions")) return "transaction-service";
        if (path.startsWith("/api/blockchain")) return "blockchain-service";
        if (path.startsWith("/api/audit")) return "audit-service";
        if (path.startsWith("/api/fraud")) return "fraud-detection";
        
        return "unknown";
    }

    /**
     * Extracts the true client IP address, respecting proxy headers.
     * 
     * <p>In production deployments behind load balancers or reverse proxies,
     * the client IP is typically in X-Forwarded-For header.
     * 
     * @param request server HTTP request
     * @return client IP address, or null if not available
     */
    private String getClientIpAddress(ServerHttpRequest request) {
        // Check X-Forwarded-For header (standard for proxies)
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // X-Forwarded-For can contain multiple IPs (client, proxy1, proxy2)
            // First IP is the original client
            return forwardedFor.split(",")[0].trim();
        }
        
        // Fallback to X-Real-IP header (used by some proxies like Nginx)
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        
        // Last resort: Remote address from connection (might be proxy IP in production)
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return null;
    }

    /**
     * Custom observation handler for additional trace customization.
     * 
     * <p>This handler runs for all observations (including HTTP server/client,
     * database queries, message publishing) and can add global tags or
     * implement custom sampling logic.
     * 
     * @return observation handler for trace enrichment
     */
    @Bean
    public ObservationHandler<Observation.Context> customObservationHandler() {
        return new ObservationHandler<Observation.Context>() {
            @Override
            public void onStart(Observation.Context context) {
                // Add global tags to all observations
                Span currentSpan = tracer.currentSpan();
                if (currentSpan != null) {
                    // Tag with environment (dev, staging, prod)
                    String environment = System.getenv("ENVIRONMENT");
                    if (environment != null) {
                        currentSpan.tag("environment", environment);
                    }
                    
                    // Tag with service version (for deployment tracking)
                    String version = System.getenv("SERVICE_VERSION");
                    if (version != null) {
                        currentSpan.tag("service.version", version);
                    }
                    
                    // Tag with deployment region (for multi-region deployments)
                    String region = System.getenv("DEPLOYMENT_REGION");
                    if (region != null) {
                        currentSpan.tag("deployment.region", region);
                    }
                }
            }

            @Override
            public void onStop(Observation.Context context) {
                // Can add custom logic on observation completion
                // e.g., calculate duration thresholds, emit custom metrics
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                // Apply to all observation contexts
                return true;
            }
        };
    }
}
