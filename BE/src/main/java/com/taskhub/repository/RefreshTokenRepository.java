package com.taskhub.repository;

import com.taskhub.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = CURRENT_TIMESTAMP, r.replacedByHash = :newHash WHERE r.user.id = :userId AND r.revoked = false AND r.expiresAt > CURRENT_TIMESTAMP")
    int revokeAllUserTokens(@Param("userId") Long userId, @Param("newHash") String newHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = CURRENT_TIMESTAMP WHERE r.tokenHash = :tokenHash")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = CURRENT_TIMESTAMP WHERE r.user.id = :userId AND r.expiresAt < CURRENT_TIMESTAMP AND r.revoked = false")
    int purgeExpiredTokens(@Param("userId") Long userId);
}
