package com.taskhub.dto.request;

import lombok.*;

/**
 * Optional refresh token for device-scoped logout.
 * When omitted, all refresh tokens for the user are revoked.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LogoutRequest {
    private String refreshToken;
}
