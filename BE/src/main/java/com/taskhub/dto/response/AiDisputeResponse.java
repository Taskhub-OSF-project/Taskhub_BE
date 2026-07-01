package com.taskhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDisputeResponse {
    private Long taskId;
    private Long submissionId;
    private String disputeSummary;
    private String aiRecommendation;
    private String fairnessAnalysis;
    private Double employerScore; // 0-10 how fair for employer
    private Double freelancerScore; // 0-10 how fair for freelancer
    private String suggestedResolution;
    private String relevantPrecedents; // similar dispute patterns
    private LocalDateTime analyzedAt;
}
