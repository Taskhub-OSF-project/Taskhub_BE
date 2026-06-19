package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dispute_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DisputeEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "performed_by")
    private Long performedBy;

    @Column(name = "performed_by_role", length = 20)
    private String performedByRole;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "ai_recommendation", length = 50)
    private String aiRecommendation;

    @Column(name = "action_taken", length = 50)
    private String actionTaken;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
