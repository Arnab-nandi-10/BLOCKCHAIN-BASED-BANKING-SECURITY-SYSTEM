package com.bbss.audit.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration properties for JWT validation.
 *
 * <p>Bound from the {@code jwt.*} namespace in {@code application.yml}.
 * The {@code secret} must be a Base64-encoded HMAC-SHA256 key of at least
 * 256 bits (32 bytes before encoding).
 *
 * <p>Example YAML:
 * <pre>
 * jwt:
 *   secret: ${JWT_SECRET}
 *   expiration-ms: 900000
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Validated
@Getter
@Setter
public class JwtConfig {

    /**
     * Base64-encoded HMAC-SHA256 signing key shared with the {@code auth-service}.
     * Must be set via the {@code JWT_SECRET} environment variable in production.
     */
    @NotBlank(message = "JWT secret must not be blank")
    private String secret;

    /**
     * Access-token validity in milliseconds.
     * Default: 15 minutes (900 000 ms).
     */
    private long expirationMs = 900_000L;
}
