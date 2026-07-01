package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_criteria_suggestions",
       indexes = @Index(name = "idx_criteria_task", columnList = "taskId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCriteriaSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private String criteriaName;

    @Column(columnDefinition = "TEXT")
    private String criteriaDescription;

    private Integer maxScore;

    private Boolean isActive;

    private Integer orderIndex;

    @Column(columnDefinition = "TEXT")
    private String evaluationGuide;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
