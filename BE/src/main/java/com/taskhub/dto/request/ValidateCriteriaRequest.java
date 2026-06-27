package com.taskhub.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ValidateCriteriaRequest {
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Size(min = 3, message = "At least 3 acceptance criteria are required")
    private List<@jakarta.validation.constraints.NotBlank String> acceptanceCriteria;
}
