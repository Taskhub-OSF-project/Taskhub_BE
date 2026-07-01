package com.taskhub.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEvaluationRequest {

    @NotNull(message = "Submission ID is required")
    private Long submissionId;

    private Long taskId;

    private String attachmentUrl; // file to evaluate

    private String customCriteria; // JSON array of criteria to evaluate against
}
