package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFileExtractRequest {

    @NotBlank(message = "File URL is required")
    private String fileUrl;

    @Builder.Default
    private Purpose purpose = Purpose.CUSTOM;

    private String context;

    private String fileName;

    private String fileType;

    public enum Purpose {
        RESUME,
        BRIEF,
        SUBMISSION,
        CONTRACT,
        CUSTOM
    }
}
