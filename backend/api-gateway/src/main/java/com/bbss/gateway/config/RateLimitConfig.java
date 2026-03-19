package com.bbss.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Configuration
public class RateLimitConfig {

    /**
     * Key resolver for rate limiting.
     * For authenticated requests, uses the principal name (user ID).
     * Falls back to the client IP address for unauthenticated requests.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .map(java.security.Principal::getName)
            .switchIfEmpty(
                Mono.justOrEmpty(
                    exchange.getRequest().getRemoteAddress()
                )
                .map(inetSocketAddress ->
                    inetSocketAddress.getAddress().getHostAddress()
                )
                .switchIfEmpty(Mono.just("unknown"))
            );
    }

    /**
     * Redis-backed rate limiter.
     * replenishRate  = 100 tokens/second
     * burstCapacity  = 200 tokens  (allows short traffic spikes)
     * requestedTokens = 1 per request
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(100, 200, 1);
    }
}
