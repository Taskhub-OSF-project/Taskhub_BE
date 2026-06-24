package com.taskhub.repository;

import com.taskhub.entity.VerificationToken;
import com.taskhub.enums.VerificationTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByTokenHash(String tokenHash);

    /** Vô hiệu các token cùng loại còn hiệu lực của user (mỗi user 1 token/loại tại một thời điểm). */
    @Modifying
    @Query("update VerificationToken v set v.usedAt = :now "
            + "where v.userId = :userId and v.type = :type and v.usedAt is null")
    int invalidateActive(@Param("userId") Long userId,
                         @Param("type") VerificationTokenType type,
                         @Param("now") java.time.LocalDateTime now);
}
