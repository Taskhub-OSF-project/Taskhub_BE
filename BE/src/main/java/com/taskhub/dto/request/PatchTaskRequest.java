package com.taskhub.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PatchTaskRequest {
    @Size(max = 255)
    private String title;

    private String description;

    @DecimalMin("1.0")
    private BigDecimal budget;

    @Future
    private LocalDateTime deadline;

    @Size(max = 100)
    private String category;

    @Size(min = 3, message = "At least 3 acceptance criteria are required")
    private List<@NotBlank String> acceptanceCriteria;
}

