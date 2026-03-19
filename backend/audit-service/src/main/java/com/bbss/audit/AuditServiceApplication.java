package com.bbss.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Audit Service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Consume Kafka events from transaction, fraud-detection, and tenant services</li>
 *   <li>Persist immutable audit records to PostgreSQL</li>
 *   <li>Anchor every audit record on the blockchain ledger via the blockchain-service</li>
 *   <li>Expose a REST API for querying the audit trail</li>
 * </ul>
 *
 * <p>{@link EnableJpaAuditing} activates {@code @CreatedDate} and
 * {@code @LastModifiedDate} on all {@code @Entity} classes.
 *
 * <p>{@link EnableFeignClients} bootstraps OpenFeign proxies for the
 * {@code blockchain-service} HTTP client.
 *
 * <p>{@link EnableScheduling} enables the {@code @Scheduled} retry job that
 * reprocesses PENDING and FAILED audit entries.
 */
@SpringBootApplication
@EnableFeignClients
@EnableJpaAuditing
@EnableScheduling
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
