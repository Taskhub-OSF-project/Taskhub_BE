package com.taskhub.repository;

import com.taskhub.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Query("SELECT p FROM PasswordResetToken p WHERE p.user.id = :userId AND p.used = false AND p.expiresAt > CURRENT_TIMESTAMP ORDER BY p.createdAt DESC")
    Optional<PasswordResetToken> findValidTokenByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.expiresAt < CURRENT_TIMESTAMP OR p.used = true")
    void purgeExpiredAndUsed();
}
