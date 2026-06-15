package com.taskhub.entity;

import com.taskhub.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(name = "tasks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(length = 100)
    private String category;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal budget;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hirer_id", nullable = false)
    private User hirer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AcceptanceCriteria> acceptanceCriteria = new ArrayList<>();

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @Column(columnDefinition = "TEXT")
    private String submissionAIResultJson;

    private LocalDateTime latestPrecheckAt;

    private Long precheckStudentId;

    private Boolean precheckCanSubmit;

    @Column(columnDefinition = "TEXT")
    private String precheckSubmittedFilePathsJson;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer revisionCount = 0;

    @Column(length = 500)
    private String disputeReason;

    @Column(columnDefinition = "TEXT")
    private String disputeDescription;

    @Column(columnDefinition = "TEXT")
    private String disputeAiReportJson;

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
