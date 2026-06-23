package com.taskhub.dto.response;

import com.taskhub.enums.Role;
import lombok.*;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthResponse {
    private String token;
    private String refreshToken;
    private Long userId;
    private String email;
    private String fullName;
    private Role role;
    /** Unix timestamp (seconds) when access token expires */
    private Long expiresAt;
}
