package com.bbss.blockchain.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j Configuration for Blockchain Service.
 * 
 * <p>Configures circuit breakers, bulkheads, and retry policies for:
 * <ul>
 *   <li>Hyperledger Fabric Gateway gRPC calls
 *   <li>External blockchain network operations
 *   <li>Kafka message publishing with retry
 * </ul>
 * 
 * <p><strong>Circuit Breaker Patterns:</strong>
 * <ul>
 *   <li><strong>fabricGateway</strong>: 30% failure threshold, 20s open duration
 *       - Protects against Fabric network outages
 *       - Fallback: Return PENDING_VERIFICATION status
 *   <li><strong>Bulkhead</strong>: Max 5 concurrent Fabric Gateway calls
 *       - Prevents thread pool exhaustion
 *       - Isolates Fabric operations from other service calls
 * </ul>
 * 
 * @see io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
 * @see io.github.resilience4j.bulkhead.annotation.Bulkhead
 */
@Slf4j
@Configuration
public class ResilienceConfig {

    /**
     * Event consumer that logs circuit breaker state transitions.
     * 
     * <p>Emits structured logs for:
     * <ul>
     *   <li>Circuit breaker creation
     *   <li>Circuit breaker removal
     *   <li>Circuit breaker configuration replacement
     * </ul>
     * 
     * <p>These logs are critical for observability and debugging circuit breaker behavior.
     * 
     * <p>Note: This is not exposed as a @Bean since resilience4j-spring-boot3 already
     * provides a circuitBreakerRegistryEventConsumer bean via auto-configuration.
     * This method shows the pattern for custom event consumer registration if needed.
     * 
     * @param circuitBreakerRegistry registry managing all circuit breakers
     * @return registry event consumer for logging
     */
    public RegistryEventConsumer<io.github.resilience4j.circuitbreaker.CircuitBreaker> customCircuitBreakerRegistryEventConsumer(
            CircuitBreakerRegistry circuitBreakerRegistry) {
        
        return new RegistryEventConsumer<io.github.resilience4j.circuitbreaker.CircuitBreaker>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> entryAddedEvent) {
                io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
                
                log.info("Circuit breaker '{}' created with config: failureRateThreshold={}, slidingWindowSize={}",
                    circuitBreaker.getName(),
                    circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold(),
                    circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize());
                
                // Register event listeners for state transitions
                circuitBreaker.getEventPublisher()
                    .onStateTransition(event -> {
                        log.warn("Circuit breaker '{}' transitioned from {} to {}",
                            event.getCircuitBreakerName(),
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                    })
                    .onFailureRateExceeded(event -> {
                        log.error("Circuit breaker '{}' failure rate exceeded: {}%",
                            event.getCircuitBreakerName(),
                            event.getFailureRate());
                    })
                    .onSlowCallRateExceeded(event -> {
                        log.warn("Circuit breaker '{}' slow call rate exceeded: {}%",
                            event.getCircuitBreakerName(),
                            event.getSlowCallRate());
                    })
                    .onCallNotPermitted(event -> {
                        log.debug("Circuit breaker '{}' rejected call (state: OPEN)",
                            event.getCircuitBreakerName());
                    });
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> entryRemoveEvent) {
                log.info("Circuit breaker '{}' removed", 
                    entryRemoveEvent.getRemovedEntry().getName());
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> entryReplacedEvent) {
                log.info("Circuit breaker '{}' configuration replaced",
                    entryReplacedEvent.getNewEntry().getName());
            }
        };
    }
}
