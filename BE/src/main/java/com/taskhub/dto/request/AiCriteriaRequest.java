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
public class AiCriteriaRequest {

    @NotNull(message = "Task ID is required")
    private Long taskId;

    private String taskDescription;

    private String taskCategory; // DESIGN, WRITING, CODING, TRANSLATION, VIDEO, OTHER

    private String attachmentUrl; // file reference for AI to analyze

    private Integer numSuggestions; // how many criteria to suggest (default 5)
}
