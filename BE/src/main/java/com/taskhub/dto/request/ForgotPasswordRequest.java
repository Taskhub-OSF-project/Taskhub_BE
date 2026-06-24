package com.taskhub.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ForgotPasswordRequest {
    @NotBlank @Email
    private String email;
}
