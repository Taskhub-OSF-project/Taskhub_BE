package com.taskhub.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request để bắt đầu đánh giá submission.
 * AI sẽ phân tích submission dựa trên các tiêu chí của task.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRequest {

    @NotNull(message = "Submission ID is required")
    private Long submissionId;

    /**
     * Danh sách ID của các tiêu chí cần đánh giá.
     * Nếu null hoặc empty → đánh giá tất cả criteria của task.
     */
    private List<Long> criteriaIds;

    /**
     * Trọng số tùy chỉnh cho từng tiêu chí (theo thứ tự criteriaIds).
     * Tổng trọng số nên = 1.0 hoặc có thể để null (dùng mặc định = bằng nhau).
     */
    private List<Double> customWeights;

    /**
     * File URL override (nếu submission có nhiều file và muốn đánh giá file khác).
     */
    private String fileUrl;
}
