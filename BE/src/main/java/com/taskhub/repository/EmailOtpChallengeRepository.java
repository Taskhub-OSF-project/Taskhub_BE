package com.taskhub.repository;

import com.taskhub.entity.EmailOtpChallenge;
import com.taskhub.enums.EmailOtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailOtpChallengeRepository extends JpaRepository<EmailOtpChallenge, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailOtpChallenge> findByChallengeId(UUID challengeId);

    @Modifying
    @Query("update EmailOtpChallenge c set c.usedAt = :now "
            + "where c.userId = :userId and c.purpose = :purpose and c.usedAt is null")
    int invalidateActive(@Param("userId") Long userId,
                         @Param("purpose") EmailOtpPurpose purpose,
                         @Param("now") LocalDateTime now);
}
