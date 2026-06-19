package com.taskhub.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateMilestoneRequest {
    @NotBlank @Size(max = 255)
    private String title;

    private String description;

    @NotNull @DecimalMin(value = "0.01")
    private BigDecimal amount;

    private LocalDateTime dueDate;
}
