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
public class AiCriteriaResponse {
    private List<CriteriaSuggestion> suggestions;
    private String reasoning;
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriteriaSuggestion {
        private String name;
        private String description;
        private Integer maxScore;
        private String evaluationGuide; // how AI suggests evaluating this criterion
    }
}
