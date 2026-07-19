package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmailOtpResendRequest {
    @NotBlank
    private String challengeId;
}
