package com.taskhub.dto.request;

import com.taskhub.enums.Role;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 6)
    private String password;
    @NotBlank
    private String fullName;
    @NotNull
    private Role role;
}
