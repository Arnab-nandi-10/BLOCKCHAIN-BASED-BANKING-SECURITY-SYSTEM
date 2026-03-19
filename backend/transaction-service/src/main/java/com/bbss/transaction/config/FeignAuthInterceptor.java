package com.bbss.transaction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign configuration that propagates the incoming {@code Authorization} header
 * to all outbound service-to-service Feign calls.
 *
 * <p>This is required because both the fraud-detection service and the
 * blockchain-service validate JWT Bearer tokens on every request.  Without
 * forwarding the caller's token the downstream services return 403 / 401.</p>
 */
@Configuration
@Slf4j
public class FeignAuthInterceptor {

    @Bean
    public RequestInterceptor authorizationHeaderInterceptor() {
        return (RequestTemplate template) -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                log.debug("No active request context — skipping Authorization header forwarding");
                return;
            }

            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                template.header("Authorization", authHeader);
            } else {
                log.debug("No Bearer token in incoming request — downstream calls will be unauthenticated");
            }
        };
    }
}
