package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerateTaskRequest {

    @NotBlank(message = "Brief is required")
    @Size(min = 20, max = 5000, message = "Brief must be between 20 and 5000 characters")
    private String brief;

    private String category;

    private Long sessionId;

    @Builder.Default
    private Language language = Language.VIETNAMESE;

    public enum Language {
        VIETNAMESE,
        ENGLISH
    }
}
