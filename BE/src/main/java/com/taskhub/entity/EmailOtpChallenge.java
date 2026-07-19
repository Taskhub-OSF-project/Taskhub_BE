package com.taskhub.entity;

import com.taskhub.enums.EmailOtpPurpose;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_otp_challenges", indexes = {
        @Index(name = "idx_email_otp_challenge_id", columnList = "challengeId", unique = true),
        @Index(name = "idx_email_otp_user_purpose", columnList = "userId,purpose")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmailOtpChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID challengeId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EmailOtpPurpose purpose;

    @Column(nullable = false)
    @Builder.Default
    private Integer failedAttempts = 0;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime lastSentAt = LocalDateTime.now();

    public boolean isUsed() {
        return usedAt != null;
    }
}
