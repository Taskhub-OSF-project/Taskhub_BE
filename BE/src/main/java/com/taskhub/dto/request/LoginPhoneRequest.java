package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LoginPhoneRequest {
    @NotBlank
    private String phone;
    @NotBlank
    private String password;
}
