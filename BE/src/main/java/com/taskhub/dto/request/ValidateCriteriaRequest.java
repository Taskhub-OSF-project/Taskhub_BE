package com.taskhub.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ValidateCriteriaRequest {
    @NotEmpty
    private List<@jakarta.validation.constraints.NotBlank String> acceptanceCriteria;
}
