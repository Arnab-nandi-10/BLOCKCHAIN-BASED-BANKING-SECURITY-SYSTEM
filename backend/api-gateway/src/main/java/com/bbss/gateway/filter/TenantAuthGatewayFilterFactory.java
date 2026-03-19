package com.bbss.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Gateway filter factory that enforces tenant context on protected routes.
 *
 * It relies on {@link JwtAuthenticationFilter} having already validated the
 * JWT and stored the {@code tenantId} as an exchange attribute.  If the
 * attribute is absent (unauthenticated request that bypassed the JWT filter,
 * or a token with no tenantId claim), the request is rejected with 401.
 */
@Component
@Slf4j
public class TenantAuthGatewayFilterFactory
        extends AbstractGatewayFilterFactory<TenantAuthGatewayFilterFactory.Config> {

    private static final String ATTR_TENANT_ID = "tenantId";

    public TenantAuthGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String tenantId = exchange.getAttribute(ATTR_TENANT_ID);
            if (tenantId == null || tenantId.isBlank()) {
                log.warn("Tenant auth filter: missing tenantId for path {}",
                        exchange.getRequest().getPath());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }

    /** No additional configuration fields required. */
    public static class Config {
    }
}
