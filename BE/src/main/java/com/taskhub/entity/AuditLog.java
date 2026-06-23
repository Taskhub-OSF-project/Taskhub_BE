package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tracks business-level actions (task created, submission, review, etc.)
 * for compliance and accountability.
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_logs_user", columnList = "user_id"),
    @Index(name = "idx_audit_logs_entity", columnList = "entity_type,entity_id"),
    @Index(name = "idx_audit_logs_created_at", columnList = "created_at"),
    @Index(name = "idx_audit_logs_action", columnList = "action"),
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_email_hash")
    private String userEmailHash;

    @Column(name = "action", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(name = "entity_type", length = 50)
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_name", length = 255)
    private String entityName;

    @Column(length = 50)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String changes;          // JSON: field-level before/after diff

    @Column(columnDefinition = "TEXT")
    private String metadata;         // JSON: action-specific context

    @Column(length = 50)
    private String outcome;          // SUCCESS, FAILURE

    @Column(length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void init() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum AuditAction {
        // Auth actions (mirror SecurityEvent but business-logged)
        USER_REGISTERED,
        USER_LOGIN,
        USER_LOGOUT,
        PASSWORD_CHANGED,
        EMAIL_VERIFIED,
        // Task lifecycle
        TASK_CREATED,
        TASK_UPDATED,
        TASK_DELETED,
        TASK_POSTED,
        TASK_ASSIGNED,
        TASK_UNLOCKED,
        TASK_ESCROW_FUNDED,
        TASK_SUBMITTED,
        TASK_REVISION_REQUESTED,
        TASK_COMPLETED,
        TASK_DISPUTED,
        // Application
        APPLICATION_SUBMITTED,
        APPLICATION_ACCEPTED,
        APPLICATION_REJECTED,
        // Submission
        SUBMISSION_CREATED,
        SUBMISSION_PRECHECKED,
        SUBMISSION_APPROVED,
        SUBMISSION_REVISION_REQUESTED,
        // Review
        REVIEW_SUBMITTED,
        REVIEW_UPDATED,
        // Portfolio
        PORTFOLIO_CREATED,
        PORTFOLIO_UPDATED,
        PORTFOLIO_DELETED,
        // Milestone
        MILESTONE_CREATED,
        MILESTONE_UPDATED,
        MILESTONE_DELETED,
        MILESTONE_FUNDED,
        MILESTONE_RELEASED,
        // Wallet
        WALLET_TOP_UP,
        WALLET_WITHDRAWAL,
        ESCROW_DEPOSIT,
        ESCROW_RELEASE,
        ESCROW_REFUND,
        // Admin
        USER_ROLE_CHANGED,
        USER_BANNED,
        USER_UNBANNED,
    }

    public enum EntityType {
        USER,
        TASK,
        SUBMISSION,
        REVIEW,
        PORTFOLIO,
        MILESTONE,
        WALLET_TRANSACTION,
        ESCROW,
        NOTIFICATION,
    }
}
