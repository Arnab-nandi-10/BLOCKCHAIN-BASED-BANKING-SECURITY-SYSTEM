package com.bbss.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async execution configuration for the audit-service.
 *
 * <p>Defines a dedicated thread pool ({@code auditBlockchainExecutor}) used
 * exclusively by {@link BlockchainCommitService#commitToBlockchainAsync} for
 * asynchronous blockchain ledger submissions.
 *
 * <p>Pool sizing rationale:
 * <ul>
 *   <li>{@code corePoolSize=5}     – minimum threads kept alive; handles steady-state load</li>
 *   <li>{@code maxPoolSize=20}     – burst capacity for spikes in ingestion rate</li>
 *   <li>{@code queueCapacity=100}  – back-pressure buffer; prevents unbounded task accumulation</li>
 * </ul>
 *
 * <p>When the queue is full and all 20 threads are busy, the executor applies
 * the default {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy}
 * (via Spring's {@link ThreadPoolTaskExecutor}), causing the calling thread to
 * execute the task directly and thereby applying natural back-pressure.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Thread pool for asynchronous blockchain commit operations.
     *
     * <p>Bean name {@code "auditBlockchainExecutor"} matches the value passed
     * to {@code @Async("auditBlockchainExecutor")} in
     * {@link BlockchainCommitService}.
     */
    @Bean(name = "auditBlockchainExecutor")
    public Executor auditBlockchainExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("audit-blockchain-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
