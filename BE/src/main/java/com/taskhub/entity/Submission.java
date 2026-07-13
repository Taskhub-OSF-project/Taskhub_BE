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

    @Column(columnDefinition = "TEXT")
    private String fileUrl;

    @Column(columnDefinition = "TEXT")
    private String submittedFilesJson;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private Integer aiScore;

    @Column(columnDefinition = "TEXT")
    private String aiReport;

    /**
     * Điểm cuối cùng (có thể từ AI hoặc Hirer override).
     * Tính theo weighted average từ EvaluationRecords.
     */
    private Double finalScore;

    /**
     * Số sao cuối cùng (1-5), quy đổi từ finalScore.
     */
    private Integer finalStars;

    /**
     * Mô tả đánh giá cuối cùng (AI tạo hoặc Hirer nhập).
     */
    @Column(columnDefinition = "TEXT")
    private String finalAssessment;

    /**
     * Mức độ đánh giá text: Xuất sắc, Tốt, Khá, Trung bình, Chưa đạt.
     */
    private String finalRating;

    /**
     * Hirer có ghi đè kết quả AI không.
     */
    @Builder.Default
    private Boolean hirerOverridden = false;

    /**
     * ID của Hirer đã xác nhận/override đánh giá.
     */
    private Long evaluatedByHirerId;

    /**
     * Thời điểm Hirer xác nhận hoặc override đánh giá.
     */
    private LocalDateTime evaluatedAt;

    private Boolean isRevision;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<EvaluationRecord> evaluationRecords = new java.util.ArrayList<>();

    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();
}
