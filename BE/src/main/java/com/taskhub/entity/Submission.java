package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "submissions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String fileUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private Integer aiScore;

    @Column(columnDefinition = "TEXT")
    private String aiReport;

    private Boolean isRevision;

    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();
}
