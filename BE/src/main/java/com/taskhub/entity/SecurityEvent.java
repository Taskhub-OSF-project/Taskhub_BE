package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "security_events", indexes = {
        @Index(name = "idx_security_event_type", columnList = "eventType"),
        @Index(name = "idx_security_event_user", columnList = "userId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SecurityEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String eventType;

    private Long userId;

    @Column(length = 255)
    private String email;

    @Column(length = 64)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
