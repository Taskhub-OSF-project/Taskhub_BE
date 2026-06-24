package com.taskhub.entity;

import com.taskhub.enums.VerificationTokenType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Token một lần cho reset mật khẩu / verify email.
 * Lưu hash SHA-256, raw token chỉ gửi qua email.
 */
@Entity
@Table(name = "verification_tokens", indexes = {
        @Index(name = "idx_verification_token_hash", columnList = "tokenHash")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VerificationTokenType type;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** Đánh dấu thời điểm token được tiêu thụ (null nếu chưa dùng). */
    private LocalDateTime usedAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isUsed() { return usedAt != null; }
}
