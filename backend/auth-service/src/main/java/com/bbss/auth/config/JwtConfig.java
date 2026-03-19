package com.bbss.auth.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Validated
@Getter
@Setter
public class JwtConfig {

    @NotBlank(message = "JWT secret must not be blank")
    private String secret;

    private long expirationMs = 900000L;

    private long refreshExpirationMs = 86400000L;
}
