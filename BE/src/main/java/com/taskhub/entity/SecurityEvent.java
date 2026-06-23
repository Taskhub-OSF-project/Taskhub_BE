package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Tracks security/authentication events for audit and anomaly detection.
 * Persisted asynchronously via AuditService to avoid blocking the auth flow.
 */
@Entity
@Table(name = "security_events", indexes = {
    @Index(name = "idx_security_events_user", columnList = "user_id"),
    @Index(name = "idx_security_events_type", columnList = "event_type"),
    @Index(name = "idx_security_events_created_at", columnList = "created_at"),
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SecurityEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_email_hash")
    private String userEmailHash;   // hashed email for lookups without storing plaintext

    @Column(name = "event_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(nullable = false, length = 20)
    private String outcome;         // SUCCESS, FAILURE, BLOCKED

    @Column(length = 50)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 45)
    private String ipCountry;

    @Column(length = 100)
    private String ipCity;

    @Column(name = "request_path", length = 255)
    private String requestPath;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String metadata;        // JSON: extra event-specific data

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void init() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum EventType {
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        REGISTRATION,
        LOGOUT,
        LOGOUT_ALL,
        PASSWORD_CHANGE,
        PASSWORD_RESET_REQUEST,
        PASSWORD_RESET_SUCCESS,
        PASSWORD_RESET_FAILURE,
        EMAIL_VERIFICATION,
        EMAIL_VERIFICATION_FAILURE,
        TOKEN_REFRESH_SUCCESS,
        TOKEN_REFRESH_FAILURE,
        TOKEN_REVOKED,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        ACCOUNT_BANNED,
        ACCOUNT_UNBANNED,
        ROLE_CHANGED,
        PERMISSION_DENIED,
        RATE_LIMIT_EXCEEDED,
        SUSPICIOUS_ACTIVITY,
    }
}
