package com.bbss.transaction.health;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for database connectivity with timeout
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private static final int TIMEOUT_SECONDS = 2;
    private static final String VALIDATION_QUERY = "SELECT 1";

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(TIMEOUT_SECONDS)) {
                // Additional check: execute a simple query
                try (Statement stmt = conn.createStatement()) {
                    stmt.setQueryTimeout(TIMEOUT_SECONDS);
                    stmt.execute(VALIDATION_QUERY);
                }

                return Health.up()
                        .withDetail("database", "PostgreSQL")
                        .withDetail("validationQuery", VALIDATION_QUERY)
                        .withDetail("timeout", TIMEOUT_SECONDS + "s")
                        .build();
            } else {
                return Health.down()
                        .withDetail("error", "Connection validation failed")
                        .withDetail("timeout", TIMEOUT_SECONDS + "s")
                        .build();
            }
        } catch (SQLException e) {
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", "Unexpected error")
                    .withDetail("message", e.getMessage())
                    .build();
        }
    }
}
