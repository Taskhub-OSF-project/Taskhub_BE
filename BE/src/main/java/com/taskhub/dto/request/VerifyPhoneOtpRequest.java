package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VerifyPhoneOtpRequest {
    @NotBlank
    private String phone;
    @NotBlank
    private String code;
    @NotBlank
    private String type;
    private RegisterRequest registerRequest;
}
