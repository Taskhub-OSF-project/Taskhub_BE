package com.taskhub.entity;

import com.taskhub.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_user_read", columnList = "user_id,is_read"),
    @Index(name = "idx_notifications_user_created", columnList = "user_id,created_at"),
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /**
     * Legacy column kept for backward compatibility with older clients
     * and external queries that read `message` directly. Hibernate only
     * writes {@code body} from the JPA entity, so {@code message} is
     * populated by {@link #onCreate()} to satisfy the database NOT NULL
     * constraint.
     */
    @Column(length = 2000)
    private String message;

    @Column(length = 300)
    private String link;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isRead == null) isRead = false;
        if (message == null || message.isBlank()) {
            message = body;
        }
    }
}