package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PasswordResetConfirmRequest {
    @NotBlank
    private String identifier;
    @NotBlank
    private String code;
    @NotBlank
    @Size(min = 8, max = 128)
    private String newPassword;
}
