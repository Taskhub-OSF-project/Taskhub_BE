package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PhoneOtpRequest {
    @NotBlank
    private String phone;

    @NotBlank
    private String type; // REGISTRATION or RECOVERY
}
