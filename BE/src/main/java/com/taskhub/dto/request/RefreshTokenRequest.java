package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RefreshTokenRequest {
    @NotBlank
    private String refreshToken;
}
