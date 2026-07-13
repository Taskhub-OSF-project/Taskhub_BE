package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.taskhub.enums.EvaluationStatus;

import java.time.LocalDateTime;

/**
 * Lưu kết quả đánh giá AI cho từng tiêu chí của một submission.
 * AI chỉ phân tích & đối chiếu — không tự quyết điểm cuối cùng.
 */
@Entity
@Table(name = "evaluation_records",
       indexes = {
           @Index(name = "idx_eval_submission", columnList = "submissionId"),
           @Index(name = "idx_eval_criteria", columnList = "criteriaId")
       },
       uniqueConstraints = @UniqueConstraint(name = "uk_eval_submission_criteria",
                                             columnNames = {"submissionId", "criteriaId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submissionId", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteriaId", nullable = false)
    private AcceptanceCriteria criteria;

    /**
     * Điểm AI đánh giá cho tiêu chí này (0.0 - maxScore).
     */
    @Column(nullable = false)
    private Double aiScore;

    /**
     * Điểm tối đa của tiêu chí này.
     */
    @Column(nullable = false)
    private Double maxScore;

    /**
     * Trọng số của tiêu chí này (tổng trọng số = 100%).
     * Default = 1.0 (bằng nhau). Hirer có thể chỉnh sửa.
     */
    @Builder.Default
    @Column(nullable = false)
    private Double weight = 1.0;

    /**
     * Trạng thái đánh giá:
     * - AI_ANALYZED: AI đã đối chiếu & gợi ý
     * - HIRER_CONFIRMED: Hirer xác nhận kết quả AI
     * - HIRER_MODIFIED: Hirer chỉnh sửa điểm
     * - HIRER_OVERRIDDEN: Hirer ghi đè hoàn toàn
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EvaluationStatus status = EvaluationStatus.AI_ANALYZED;

    /**
     * Feedback chi tiết của AI cho tiêu chí này.
     */
    @Column(columnDefinition = "TEXT")
    private String aiFeedback;

    /**
     * Feedback của Hirer (nếu chỉnh sửa).
     */
    @Column(columnDefinition = "TEXT")
    private String hirerFeedback;

    /**
     * Số sao (1-5) cho tiêu chí này — được tính từ aiScore / maxScore.
     */
    private Integer stars;

    /**
     * Người đánh giá: AI hoặc HIRER
     */
    @Builder.Default
    private String evaluatedBy = "AI";

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Tỷ lệ % đạt của tiêu chí này (aiScore / maxScore * 100).
     */
    public double getPercentage() {
        if (maxScore == null || maxScore == 0) return 0;
        return (aiScore / maxScore) * 100;
    }

    /**
     * Cập nhật stars dựa trên percentage.
     */
    public void updateStarsFromPercentage() {
        double pct = getPercentage();
        if (pct >= 95) this.stars = 5;
        else if (pct >= 85) this.stars = 4;
        else if (pct >= 70) this.stars = 3;
        else if (pct >= 50) this.stars = 2;
        else this.stars = 1;
    }
}
