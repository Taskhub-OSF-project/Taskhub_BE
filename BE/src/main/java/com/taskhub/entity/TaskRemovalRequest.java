package com.taskhub.entity;

import com.taskhub.enums.RemovalReason;
import com.taskhub.enums.RemovalStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_removal_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskRemovalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "removal_reason", nullable = false)
    private RemovalReason reason;

    @Column(name = "reason_description", columnDefinition = "TEXT")
    private String reasonDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_status_at_request", nullable = false)
    private com.taskhub.enums.TaskStatus taskStatusAtRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RemovalStatus status = RemovalStatus.PENDING;

    @Column(name = "ai_validation_result", columnDefinition = "TEXT")
    private String aiValidationResult;

    @Column(name = "ai_recommendation", length = 50)
    private String aiRecommendation;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
