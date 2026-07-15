package com.taskhub.repository;

import com.taskhub.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OtpToken o WHERE o.phone = :phone AND o.type = :type AND o.used = false AND o.expiresAt > CURRENT_TIMESTAMP ORDER BY o.createdAt DESC")
    Optional<OtpToken> findValidOtp(@Param("phone") String phone, @Param("type") String type);

    @Modifying
    @Query("DELETE FROM OtpToken o WHERE o.expiresAt < CURRENT_TIMESTAMP OR o.used = true")
    void purgeExpiredAndUsed();

    @Modifying
    @Query("DELETE FROM OtpToken o WHERE o.phone = :phone AND o.type = :type AND o.used = false")
    void deleteActiveByPhoneAndType(@Param("phone") String phone, @Param("type") String type);
}
