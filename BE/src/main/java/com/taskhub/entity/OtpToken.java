package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_tokens")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OtpToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false)
    private String phone;

    @Column(length = 6, nullable = false)
    private String code;

    @Column(length = 20, nullable = false)
    private String type;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    private void init() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean isValid() {
        return !Boolean.TRUE.equals(used) && LocalDateTime.now().isBefore(expiresAt);
    }
}
