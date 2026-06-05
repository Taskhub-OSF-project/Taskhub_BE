package com.taskhub.dto.request;

import lombok.*;
import jakarta.validation.constraints.NotBlank;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RevisionRequest {
    @NotBlank
    private String reason;
    private String description;
}
