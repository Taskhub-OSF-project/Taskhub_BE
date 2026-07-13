package com.taskhub.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request để Hirer xác nhận hoặc override đánh giá.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationOverrideRequest {

    @NotNull(message = "Submission ID is required")
    private Long submissionId;

    /**
     * Trọng số mới cho từng tiêu chí (theo thứ tự criteria ID trong response).
     * Tổng = 1.0.
     */
    private java.util.List<Double> weights;

    /**
     * Điểm mới cho từng tiêu chí (null = giữ nguyên điểm AI).
     */
    private java.util.List<Double> scores;

    /**
     * Feedback mới cho từng tiêu chí (null = giữ nguyên feedback AI).
     */
    private java.util.List<String> feedbacks;

    /**
     * Assessment tổng thể (Hirer viết).
     */
    private String overallAssessment;

    /**
     * Override toàn bộ kết quả AI (true = dùng điểm/sao từ fields bên dưới).
     */
    private Boolean fullOverride;

    /**
     * Điểm tổng mới (0-100), chỉ dùng khi fullOverride = true.
     */
    @Min(0) @Max(100)
    private Double overrideScore;

    /**
     * Số sao tổng mới (1-5), chỉ dùng khi fullOverride = true.
     */
    @Min(1) @Max(5)
    private Integer overrideStars;

    /**
     * Rating text mới (Xuất sắc, Tốt, Khá, Trung bình, Chưa đạt).
     */
    private String overrideRating;

    /**
     * Feedback tổng thể của Hirer.
     */
    private String overrideAssessment;
}
