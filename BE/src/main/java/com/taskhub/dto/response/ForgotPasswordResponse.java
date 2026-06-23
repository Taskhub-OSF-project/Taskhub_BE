package com.taskhub.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordResponse {
    private String resetLink;
    private String token;
    private boolean emailSent;
}