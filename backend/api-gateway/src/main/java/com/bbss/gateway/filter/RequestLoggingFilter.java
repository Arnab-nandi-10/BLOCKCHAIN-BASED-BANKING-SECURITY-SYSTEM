package com.bbss.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String RESPONSE_TIME_HEADER = "X-Response-Time";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_TENANT_ID = "tenantId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String method = request.getMethod().name();
        String path = request.getPath().value();
        String tenantId = request.getHeaders().getFirst(TENANT_HEADER);

        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        final String finalRequestId = requestId;
        final long startTime = System.currentTimeMillis();

        MDC.put(MDC_REQUEST_ID, finalRequestId);
        MDC.put(MDC_TENANT_ID, tenantId != null ? tenantId : "unknown");

        log.info("Incoming request - method: {}, path: {}, tenantId: {}, requestId: {}",
                method, path, tenantId, finalRequestId);

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(REQUEST_ID_HEADER, finalRequestId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        mutatedExchange.getResponse().beforeCommit(() -> {
            ServerHttpResponse response = mutatedExchange.getResponse();
            long durationMs = System.currentTimeMillis() - startTime;
            response.getHeaders().set(REQUEST_ID_HEADER, finalRequestId);
            response.getHeaders().set(RESPONSE_TIME_HEADER, durationMs + "ms");
            return Mono.empty();
        });

        return chain.filter(mutatedExchange)
                .doFinally(signalType -> {
                    ServerHttpResponse response = mutatedExchange.getResponse();
                    long durationMs = System.currentTimeMillis() - startTime;
                    int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 0;

                    log.info("Outgoing response - requestId: {}, status: {}, duration: {}ms",
                            finalRequestId, statusCode, durationMs);

                    MDC.clear();
                });
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
