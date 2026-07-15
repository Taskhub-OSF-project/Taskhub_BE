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
public class RemovalAIReport {
    private Long taskId;
    private String taskTitle;
    private String removalReason;
    private String reasonDescription;
    private String taskStatus;
    private Boolean hasAssignedFreelancer;
    private Boolean hasEscrow;
    private Boolean hasSubmissions;
    private Integer revisionCount;
    private Integer submissionCount;
    private Double escrowAmount;
    private String aiRecommendation;
    private String aiAnalysis;
    private List<String> warnings;
    private Boolean canAutoApprove;
    private Integer recentRemovalRequestCount;
    private Boolean frequentRequester;
    private Integer riskScore;
    private String moderationDecision;
    private LocalDateTime generatedAt;
}
