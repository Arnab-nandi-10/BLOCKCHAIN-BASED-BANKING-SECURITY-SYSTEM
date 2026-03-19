package com.bbss.auth.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bbss.auth.domain.model.RefreshToken;
import com.bbss.auth.domain.model.User;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
    void deleteAllByUser(@Param("user") User user);

    List<RefreshToken> findByUserAndRevokedFalse(User user);

    // Phase 2: Token rotation queries
    List<RefreshToken> findByExpiresAtBefore(LocalDateTime dateTime);

    List<RefreshToken> findByRevokedTrueAndCreatedAtBefore(LocalDateTime dateTime);

    List<RefreshToken> findByRevokedFalseAndExpiresAtBetween(LocalDateTime start, LocalDateTime end);

    long countByRevokedTrue();
}
