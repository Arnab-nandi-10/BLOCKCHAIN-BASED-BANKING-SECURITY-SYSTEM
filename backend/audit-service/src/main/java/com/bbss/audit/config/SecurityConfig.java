package com.bbss.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 configuration for the audit-service.
 *
 * <p>The service is fully stateless: no HTTP session is created or used.
 * Authentication is performed exclusively via the {@link JwtAuthFilter}
 * which is installed before Spring Security's default username/password filter.
 *
 * <p>Access policy:
 * <ul>
 *   <li>Actuator health and info endpoints are publicly accessible.</li>
 *   <li>OpenAPI documentation endpoints are publicly accessible.</li>
 *   <li>All {@code /api/v1/audit/**} endpoints require a valid JWT.
 *       Fine-grained role checks are enforced at the controller level via
 *       {@code @PreAuthorize}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // ── Public endpoints ──────────────────────────────────────
                    .requestMatchers(
                            "/actuator/health",
                            "/actuator/info"
                    ).permitAll()
                    .requestMatchers(
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html"
                    ).permitAll()
                    // ── Protected audit API ───────────────────────────────────
                    .requestMatchers("/api/v1/audit/**").authenticated()
                    // ── Everything else also requires auth ────────────────────
                    .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
