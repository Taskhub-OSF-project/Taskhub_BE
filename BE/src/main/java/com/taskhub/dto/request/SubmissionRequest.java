package com.taskhub.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SubmissionRequest {
    @NotBlank
    private String fileUrl;
    private String notes;
}
