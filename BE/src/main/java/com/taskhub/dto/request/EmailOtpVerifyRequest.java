package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmailOtpVerifyRequest {
    @NotBlank
    private String challengeId;

    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must contain exactly 6 digits")
    private String code;
}
