package com.taskhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEvaluationResponse {
    private Long submissionId;
    private Long taskId;
    private String overallAssessment;
    private Double overallScore;
    private List<CriteriaScore> criteriaScores;
    private String strengths;
    private String weaknesses;
    private String suggestions;
    private LocalDateTime evaluatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriteriaScore {
        private String criterion;
        private Double score;
        private Double maxScore;
        private String feedback;
    }
}
