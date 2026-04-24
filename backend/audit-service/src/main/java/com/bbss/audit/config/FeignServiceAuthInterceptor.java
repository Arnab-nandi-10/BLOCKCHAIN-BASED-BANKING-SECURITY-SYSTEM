package com.bbss.audit.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Adds authentication headers to blockchain-service Feign calls.
 *
 * <p>User JWTs are forwarded when available, and a shared internal service
 * token is always attached so async retries can authenticate too.</p>
 */
@Configuration
@Slf4j
public class FeignServiceAuthInterceptor {

    private static final String INTERNAL_HEADER = "X-Internal-Service-Token";

    @Value("${services.blockchain-service.internal-token}")
    private String internalServiceToken;

    @Bean
    public RequestInterceptor blockchainAuthorizationInterceptor() {
        return (RequestTemplate template) -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");

                if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                    template.header("Authorization", authHeader);
                }
            } else {
                log.debug("No active request context — relying on internal service token for Feign call");
            }

            template.header(INTERNAL_HEADER, internalServiceToken);
        };
    }
}
