package com.taskhub.entity;

import com.taskhub.enums.EscrowStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "milestones")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Milestone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MilestoneStatus status = MilestoneStatus.PENDING;

    @Column(name = "escrow_status")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EscrowStatus escrowStatus = EscrowStatus.PENDING;

    @Column(name = "funded_at")
    private LocalDateTime fundedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public enum MilestoneStatus {
        PENDING,
        ACTIVE,
        SUBMITTED,
        APPROVED,
        REJECTED,
        CANCELLED
    }
}
