package com.taskhub.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.List;

/**
 * Cập nhật profile của chính mình. Các field null = không đổi.
 * Email/role/ví không cho sửa qua endpoint này.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateProfileRequest {
    @Size(max = 255)
    private String fullName;

    @Size(max = 100)
    private String university;

    @Size(max = 100)
    private String major;

    @Size(max = 500)
    private String bio;

    @Size(max = 30)
    private List<@Size(max = 80) String> skills;

    @Size(max = 5000)
    private String experience;

    @Size(max = 500)
    @Pattern(regexp = "(?i)^$|^https://[^\\s]+$", message = "Portfolio URL must use HTTPS")
    private String portfolioUrl;

    @Size(max = 20)
    private String phone;

    @Size(max = 200)
    private String title;

    @Size(max = 20)
    private String hourlyRate;

    @Size(max = 50)
    private String availability;

    @Size(max = 20)
    private List<@Size(max = 80) String> languages;

    @Size(max = 30)
    private List<@Size(max = 255) String> certifications;

    @Size(max = 500)
    @Pattern(regexp = "(?i)^$|^https://[^\\s]+$", message = "Avatar URL must use HTTPS")
    private String avatarUrl;
}
