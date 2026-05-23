package com.taskhub.dto.response;

import com.taskhub.enums.Role;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long userId;
    private String email;
    private String fullName;
    private Role role;
}
