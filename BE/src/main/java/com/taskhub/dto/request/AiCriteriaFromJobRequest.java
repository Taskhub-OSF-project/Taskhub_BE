package com.taskhub.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCriteriaFromJobRequest {

    @NotBlank(message = "Job title is required")
    @Size(max = 255)
    private String jobTitle;

    private String jobDescription;

    @Size(max = 100)
    private String category;

    @Min(value = 3, message = "At least 3 criteria suggestions required")
    @Max(value = 10, message = "Maximum 10 criteria suggestions")
    @Builder.Default
    private Integer numSuggestions = 5;
}
