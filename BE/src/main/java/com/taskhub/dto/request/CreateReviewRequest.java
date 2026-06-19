package com.taskhub.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateReviewRequest {
    @NotNull @Min(1) @Max(5)
    private Integer rating;

    @Size(max = 1000)
    private String comment;
}
