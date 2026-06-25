package com.taskhub.dto.request;

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

    private List<String> skills;

    private String experience;

    @Size(max = 500)
    private String portfolioUrl;

    @Size(max = 20)
    private String phone;

    @Size(max = 200)
    private String title;

    @Size(max = 20)
    private String hourlyRate;

    @Size(max = 50)
    private String availability;

    private List<String> languages;

    private List<String> certifications;

    @Size(max = 500)
    private String avatarUrl;
}
