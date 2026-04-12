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
    @NotNull @DecimalMin("1.0")
    private BigDecimal budget;
    @NotNull @Future
    private LocalDateTime deadline;
    @NotEmpty
    private List<@NotBlank String> acceptanceCriteria;
}
