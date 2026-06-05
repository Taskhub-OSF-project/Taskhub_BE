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
public class SubmissionAIResult {
    private String overallStatus;
    private List<CriteriaAIResult> criteriaResults;
    private boolean canSubmit;
    private LocalDateTime evaluatedAt;
}
