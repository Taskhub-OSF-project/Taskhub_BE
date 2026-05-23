package com.taskhub.entity;

import com.taskhub.enums.CriteriaStatus;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name = "acceptance_criteria")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AcceptanceCriteria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CriteriaStatus status = CriteriaStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;
}
