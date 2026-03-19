package com.bbss.transaction.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Distributed Tracing Configuration for Transaction Service.
 * 
 * <p>Enriches traces with transaction-specific business context:
 * <ul>
 *   <li>Transaction amounts and currencies
 *   <li>Fraud detection scores and verdicts
 *   <li>Blockchain verification status
 *   <li>Transaction types and statuses
 * </ul>
 * 
 * <p>These custom tags enable powerful trace querying in Jaeger:
 * <pre>
 * - Find all transactions > $10,000: transaction.amount > 10000
 * - Find all fraud alerts: fraud.verdict = BLOCKED
 * - Find all blockchain failures: blockchain.status = FAILED
 * </pre>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TracingConfig {

    private final Tracer tracer;

    /**
     * Aspect that automatically tags transaction-related spans.
     * 
     * <p>Intercepts methods in TransactionService and adds relevant
     * business context to the current span.
     */
    @Component
    @Aspect
    @RequiredArgsConstructor
    public static class TransactionTracingAspect {

        private final Tracer tracer;

        /**
         * Enriches transaction creation spans with amount and currency.
         * 
         * @param joinPoint proceeding join point
         * @return method execution result
         * @throws Throwable if method execution fails
         */
        @Around("execution(* com.bbss.transaction.service.TransactionService.createTransaction(..))")
        public Object traceTransactionCreation(ProceedingJoinPoint joinPoint) throws Throwable {
            Span currentSpan = tracer.currentSpan();
            
            if (currentSpan != null) {
                // Extract transaction details from method arguments
                Object[] args = joinPoint.getArgs();
                if (args.length > 0 && args[0] != null) {
                    // Assuming first arg is transaction DTO with amount, currency, type
                    try {
                        Object dto = args[0];
                        
                        // Use reflection to extract fields (safer than casting)
                        Object amount = dto.getClass().getMethod("getAmount").invoke(dto);
                        if (amount instanceof BigDecimal) {
                            currentSpan.tag("transaction.amount", ((BigDecimal) amount).toString());
                        }
                        
                        Object currency = dto.getClass().getMethod("getCurrency").invoke(dto);
                        if (currency != null) {
                            currentSpan.tag("transaction.currency", currency.toString());
                        }
                        
                        Object type = dto.getClass().getMethod("getType").invoke(dto);
                        if (type != null) {
                            currentSpan.tag("transaction.type", type.toString());
                        }
                        
                        log.debug("Tagged transaction span with amount: {}, currency: {}, type: {}", 
                            amount, currency, type);
                    } catch (Exception e) {
                        log.warn("Failed to extract transaction details for tracing", e);
                    }
                }
            }
            
            return joinPoint.proceed();
        }

        /**
         * Enriches fraud detection spans with scores and verdicts.
         * 
         * @param joinPoint proceeding join point
         * @return method execution result
         * @throws Throwable if method execution fails
         */
        @Around("execution(* com.bbss.transaction.service.FraudDetectionService.checkFraud(..))")
        public Object traceFraudDetection(ProceedingJoinPoint joinPoint) throws Throwable {
            Span currentSpan = tracer.currentSpan();
            
            Object result = joinPoint.proceed();
            
            if (currentSpan != null && result != null) {
                try {
                    // Extract fraud score and verdict from result
                    Object score = result.getClass().getMethod("getFraudScore").invoke(result);
                    if (score instanceof Double) {
                        currentSpan.tag("fraud.score", String.format("%.4f", (Double) score));
                        
                        // Add high-cardinality tag for easy filtering of high-risk transactions
                        double scoreValue = (Double) score;
                        if (scoreValue >= 0.60) {
                            currentSpan.tag("fraud.risk_level", "HIGH");
                        } else if (scoreValue >= 0.35) {
                            currentSpan.tag("fraud.risk_level", "MEDIUM");
                        } else {
                            currentSpan.tag("fraud.risk_level", "LOW");
                        }
                    }
                    
                    Object verdict = result.getClass().getMethod("getVerdict").invoke(result);
                    if (verdict != null) {
                        currentSpan.tag("fraud.verdict", verdict.toString());
                    }
                    
                    log.debug("Tagged fraud detection span with score: {}, verdict: {}", score, verdict);
                } catch (Exception e) {
                    log.warn("Failed to extract fraud details for tracing", e);
                }
            }
            
            return result;
        }

        /**
         * Enriches blockchain verification spans with status.
         * 
         * @param joinPoint proceeding join point
         * @return method execution result
         * @throws Throwable if method execution fails
         */
        @Around("execution(* com.bbss.transaction.client.BlockchainClient.recordTransaction(..))")
        public Object traceBlockchainVerification(ProceedingJoinPoint joinPoint) throws Throwable {
            Span currentSpan = tracer.currentSpan();
            
            if (currentSpan != null) {
                currentSpan.tag("blockchain.operation", "record_transaction");
            }
            
            try {
                Object result = joinPoint.proceed();
                
                if (currentSpan != null) {
                    currentSpan.tag("blockchain.status", "SUCCESS");
                    
                    // Extract blockchain transaction ID if available
                    if (result != null) {
                        try {
                            Object txHash = result.getClass().getMethod("getBlockchainTransactionId").invoke(result);
                            if (txHash != null) {
                                currentSpan.tag("blockchain.tx_hash", txHash.toString());
                            }
                        } catch (Exception e) {
                            log.debug("Blockchain response does not contain transaction ID");
                        }
                    }
                }
                
                return result;
            } catch (Exception e) {
                if (currentSpan != null) {
                    currentSpan.tag("blockchain.status", "FAILED");
                    currentSpan.tag("blockchain.error", e.getClass().getSimpleName());
                }
                throw e;
            }
        }
    }

    /**
     * Custom observation handler for transaction-specific metrics.
     * 
     * @return observation handler
     */
    @Bean
    public ObservationHandler<Observation.Context> transactionObservationHandler() {
        return new ObservationHandler<Observation.Context>() {
            @Override
            public void onStart(Observation.Context context) {
                Span currentSpan = tracer.currentSpan();
                if (currentSpan != null) {
                    // Add service-specific tags
                    currentSpan.tag("service.name", "transaction-service");
                    currentSpan.tag("service.layer", "backend");
                }
            }

            @Override
            public void onStop(Observation.Context context) {
                // Can calculate and emit custom metrics here
                // e.g., transaction processing duration histograms
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        };
    }
}
