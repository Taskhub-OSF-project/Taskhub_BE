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
public class AiProgressRequest {

    @NotNull(message = "Task ID is required")
    private Long taskId;

    private Long submissionId; // optional, for specific submission progress

    private String userRole; // EMPLOYER or FREELANCER
}
