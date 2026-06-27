package com.taskhub.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateTaskRequest {
    @NotBlank
    private String title;
    @NotBlank
    private String description;

    @Size(max = 100)
    private String category;

    @NotNull @DecimalMin("1.0")
    private BigDecimal budget;
    @NotNull @Future
    private LocalDateTime deadline;
    @NotNull
    @Size(min = 3, message = "At least 3 acceptance criteria are required")
    private List<@NotBlank String> acceptanceCriteria;
}
