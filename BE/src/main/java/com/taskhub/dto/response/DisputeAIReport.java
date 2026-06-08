package com.taskhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Báo cáo AI structured cho dispute.
 * Trả về từ GET /api/tasks/{id}/dispute/report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeAIReport {

    private Long taskId;

    /** Danh sách đánh giá từng criterion. */
    private List<CriterionAssessment> assessments;

    /**
     * Khuyến nghị tổng hợp: RELEASE_PAYMENT | REQUEST_REVISION | ESCALATE.
     * - >= 70% MET → RELEASE_PAYMENT
     * - <= 30% MET → REQUEST_REVISION
     * - còn lại     → ESCALATE
     */
    private String recommendation;

    /** Tỷ lệ % criterion được đánh là MET (0–100). */
    private int metPercentage;

    private String disputeReason;
    private String disputeDescription;

    private LocalDateTime reportGeneratedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionAssessment {
        private int criterionIndex;
        private String criterion;
        /** MET | NOT_MET */
        private String assessment;
        private boolean met;
        private String evidence;
    }
}
