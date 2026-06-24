package com.taskhub.dto.request;

import com.taskhub.enums.Role;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 8, max = 128)
    private String password;
    @NotBlank
    private String fullName;

    @Size(max = 100)
    private String university;

    @Size(max = 100)
    private String major;

    @NotNull
    private Role role;

    private LocalDate dateOfBirth;

    private String phone;
}
