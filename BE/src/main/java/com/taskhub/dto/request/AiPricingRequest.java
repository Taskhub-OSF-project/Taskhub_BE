package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class AiPricingRequest {

    @NotBlank(message = "Task title is required")
    @Size(max = 255, message = "Task title too long")
    private String taskTitle;

    private String taskDescription;

    @Size(max = 100)
    private String category;

    private LocalDateTime deadline;

    @Builder.Default
    private Complexity complexity = Complexity.MEDIUM;

    private List<String> skillsRequired;

    private BigDecimal expectedBudget;

    public enum Complexity {
        LOW,
        MEDIUM,
        HIGH
    }
}
