package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDisputeRequest {

    @NotNull(message = "Task ID is required")
    private Long taskId;

    @NotNull(message = "Submission ID is required")
    private Long submissionId;

    @NotBlank(message = "Dispute description is required")
    private String disputeDescription;

    private String employerClaim;
    private String freelancerClaim;

    private String attachmentUrl;
}
