package com.taskhub.dto.request;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserProfileUpdateRequest {
    private String fullName;
    private String university;
    private String school;   // alias for university (FE convention)
    private String major;
    private String bio;
    private List<String> skills;
    private String experience;
    private String portfolioUrl;
    private String phone;
    private String title;
    private String hourlyRate;
    private String availability;
    private List<String> languages;
    private List<String> certifications;
    private String avatarUrl;
    private LocalDate dateOfBirth;
}
