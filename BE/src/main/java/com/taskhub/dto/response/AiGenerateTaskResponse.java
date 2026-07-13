package com.taskhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerateTaskResponse {

    private String title;
    private String description;
    private String category;
    private BigDecimal suggestedBudget;
    private LocalDateTime suggestedDeadline;
    private List<String> skillsRequired;
    private String difficultyLevel;
    private Integer estimatedHours;
    private List<AiCriteriaResponse.CriteriaSuggestion> suggestedCriteria;
    private String estimatedDuration;
    private String rawAiContent;
    private List<String> warnings;
    private LocalDateTime generatedAt;
}
