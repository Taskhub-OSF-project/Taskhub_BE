package com.taskhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response trả về sau khi AI phân tích submission.
 * Chứa kết quả đánh giá từng tiêu chí + điểm tổng + sao.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResponse {

    private Long submissionId;
    private Long taskId;

    /** Danh sách kết quả từng tiêu chí */
    private List<CriteriaEvaluationResult> criteriaResults;

    /** Điểm tổng (0-100), tính theo trọng số */
    private Double totalScore;

    /** Số sao (1-5), quy đổi từ totalScore */
    private Integer stars;

    /** Mức đánh giá text */
    private String ratingLabel;

    /** Assessment tổng thể từ AI */
    private String overallAssessment;

    /** Điểm số từng tiêu chí (cũ - để backward compat) */
    private String strengths;
    private String weaknesses;
    private String suggestions;

    /** AI đã ghi nhận những tiêu chí nào đạt */
    private Integer criteriaMetCount;
    private Integer criteriaTotalCount;

    /** Trạng thái: AI_ANALYZED = chờ Hirer xác nhận */
    private String evaluationStatus;

    /** Ai có bị Hirer override chưa */
    private Boolean isHirerOverridden;

    private LocalDateTime evaluatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriteriaEvaluationResult {
        private Long criteriaId;
        private String criteriaDescription;
        private Double weight;
        private Double aiScore;
        private Double maxScore;
        private Double percentage;
        private Integer stars;
        private String status;
        private String aiFeedback;
    }
}
