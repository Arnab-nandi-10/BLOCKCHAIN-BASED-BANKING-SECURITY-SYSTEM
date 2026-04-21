package com.bbss.gateway.config;

import com.bbss.gateway.filter.TenantAuthGatewayFilterFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Autowired
    private TenantAuthGatewayFilterFactory tenantAuthGatewayFilterFactory;

    @Autowired
    private RedisRateLimiter redisRateLimiter;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

            // Auth Service - preserve downstream auth responses; no retry/circuit breaker
            // so login/register requests are never duplicated or masked by a fallback 503.
            .route("auth-service", r -> r
                .path("/api/v1/auth/**")
                .uri("http://auth-service:8081")
            )

            // Tenant Service
            .route("tenant-service", r -> r
                .path("/api/v1/tenants/**")
                .filters(f -> f
                    .filter(tenantAuthGatewayFilterFactory.apply(new TenantAuthGatewayFilterFactory.Config()))
                    .preserveHostHeader()
                    .retry(config -> config
                        .setRetries(3)
                        .setMethods(
                            org.springframework.http.HttpMethod.GET
                        )
                    )
                    .circuitBreaker(config -> config
                        .setName("tenant-service")
                        .setFallbackUri("forward:/fallback")
                    )
                )
                .uri("http://tenant-service:8082")
            )

            // Transaction Service with rate limiting
            .route("transaction-service", r -> r
                .path("/api/v1/transactions/**")
                .filters(f -> f
                    .preserveHostHeader()
                    .filter(tenantAuthGatewayFilterFactory.apply(new TenantAuthGatewayFilterFactory.Config()))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter)
                        .setKeyResolver(new org.springframework.cloud.gateway.filter.ratelimit.PrincipalNameKeyResolver())
                    )
                    .retry(config -> config
                        .setRetries(3)
                        .setMethods(
                            org.springframework.http.HttpMethod.GET
                        )
                    )
                    .circuitBreaker(config -> config
                        .setName("transaction-service")
                        .setFallbackUri("forward:/fallback")
                    )
                )
                .uri("http://transaction-service:8083")
            )

            // Audit Service
            .route("audit-service", r -> r
                .path("/api/v1/audit/**")
                .filters(f -> f
                    .filter(tenantAuthGatewayFilterFactory.apply(new TenantAuthGatewayFilterFactory.Config()))
                    .retry(config -> config
                        .setRetries(3)
                        .setMethods(
                            org.springframework.http.HttpMethod.GET
                        )
                    )
                    .circuitBreaker(config -> config
                        .setName("audit-service")
                        .setFallbackUri("forward:/fallback")
                    )
                )
                .uri("http://audit-service:8085")
            )

            // Blockchain Service
            .route("blockchain-service", r -> r
                .path("/api/v1/blockchain/**")
                .filters(f -> f
                    .filter(tenantAuthGatewayFilterFactory.apply(new TenantAuthGatewayFilterFactory.Config()))
                    .retry(config -> config
                        .setRetries(3)
                        .setMethods(
                            org.springframework.http.HttpMethod.GET
                        )
                    )
                    .circuitBreaker(config -> config
                        .setName("blockchain-service")
                        .setFallbackUri("forward:/fallback")
                    )
                )
                .uri("http://blockchain-service:8084")
            )

            // Fraud Detection Service
            .route("fraud-service", r -> r
                .path("/api/v1/fraud/**")
                .filters(f -> f
                    .filter(tenantAuthGatewayFilterFactory.apply(new TenantAuthGatewayFilterFactory.Config()))
                    .retry(config -> config
                        .setRetries(3)
                        .setMethods(
                            org.springframework.http.HttpMethod.GET
                        )
                    )
                    .circuitBreaker(config -> config
                        .setName("fraud-detection")
                        .setFallbackUri("forward:/fallback")
                    )
                )
                .uri("http://fraud-detection:8000")
            )

            .build();
    }
}
