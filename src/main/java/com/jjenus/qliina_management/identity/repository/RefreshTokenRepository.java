package com.jjenus.qliina_management.identity.repository;

import com.jjenus.qliina_management.identity.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
    
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now OR t.revokedAt IS NOT NULL")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);
}
