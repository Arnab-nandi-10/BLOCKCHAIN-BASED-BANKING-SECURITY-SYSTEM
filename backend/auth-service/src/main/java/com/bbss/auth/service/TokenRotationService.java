package com.bbss.auth.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bbss.auth.domain.model.RefreshToken;
import com.bbss.auth.domain.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Background service for proactive token rotation and cleanup.
 *
 * <p><strong>Phase 2: Security Hardening - Automatic Token Rotation</strong></p>
 *
 * <ul>
 *   <li><strong>Expired Token Cleanup</strong>: Purges expired/revoked tokens daily</li>
 *   <li><strong>Rotation Warnings</strong>: Logs tokens approaching expiration</li>
 *   <li><strong>Security Auditing</strong>: Tracks token usage patterns</li>
 * </ul>
 *
 * @see com.bbss.auth.service.AuthService#refreshToken(String)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRotationService {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Cleanup expired refresh tokens from the database.
     *
     * <p>Runs daily at 2:00 AM to remove:
     * <ul>
     *   <li>Expired tokens (expiresAt < now)</li>
     *   <li>Revoked tokens older than 30 days</li>
     * </ul>
     * </p>
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting expired token cleanup job");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime revokedThreshold = now.minusDays(30);

        // Delete expired tokens
        List<RefreshToken> expiredTokens = refreshTokenRepository.findByExpiresAtBefore(now);
        if (!expiredTokens.isEmpty()) {
            refreshTokenRepository.deleteAll(expiredTokens);
            log.info("Deleted {} expired refresh tokens", expiredTokens.size());
        }

        // Delete old revoked tokens
        List<RefreshToken> oldRevokedTokens = refreshTokenRepository
                .findByRevokedTrueAndCreatedAtBefore(revokedThreshold);
        if (!oldRevokedTokens.isEmpty()) {
            refreshTokenRepository.deleteAll(oldRevokedTokens);
            log.info("Deleted {} old revoked refresh tokens", oldRevokedTokens.size());
        }

        log.info("Token cleanup job completed");
    }

    /**
     * Monitor tokens approaching expiration and log warnings.
     *
     * <p>Runs every 6 hours to identify tokens expiring within 24 hours.
     * Enables proactive monitoring for long-lived sessions.</p>
     */
    @Scheduled(fixedRate = 21600000) // Every 6 hours
    @Transactional(readOnly = true)
    public void monitorTokenExpiration() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expirationThreshold = now.plusHours(24);

        List<RefreshToken> expiringTokens = refreshTokenRepository
                .findByRevokedFalseAndExpiresAtBetween(now, expirationThreshold);

        if (!expiringTokens.isEmpty()) {
            log.warn("Found {} refresh tokens expiring within 24 hours", expiringTokens.size());
            
            expiringTokens.forEach(token -> {
                long hoursUntilExpiry = java.time.Duration.between(now, token.getExpiresAt()).toHours();
                log.debug("Token for user {} (tenant: {}) expires in {} hours",
                        token.getUser().getEmail(), token.getTenantId(), hoursUntilExpiry);
            });
        }
    }

    /**
     * Generate token usage statistics for security auditing.
     *
     * <p>Runs daily at 3:00 AM to log metrics for:
     * <ul>
     *   <li>Active refresh tokens per tenant</li>
     *   <li>Revoked token count</li>
     *   <li>Average token lifetime</li>
     * </ul>
     * </p>
     */
    @Scheduled(cron = "0 0 3 * * *") // Daily at 3 AM
    @Transactional(readOnly = true)
    public void generateTokenStatistics() {
        log.info("Generating token usage statistics");

        long totalTokens = refreshTokenRepository.count();
        long revokedTokens = refreshTokenRepository.countByRevokedTrue();
        long activeTokens = totalTokens - revokedTokens;

        log.info("Token Statistics - Total: {}, Active: {}, Revoked: {}",
                totalTokens, activeTokens, revokedTokens);

        if (revokedTokens > activeTokens * 2) {
            log.warn("High revoked token ratio detected: {} revoked vs {} active. " +
                    "Consider investigating potential security issues.", revokedTokens, activeTokens);
        }
    }
}
