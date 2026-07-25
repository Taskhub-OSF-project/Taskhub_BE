package com.taskhub.dto.response;

import com.taskhub.enums.Role;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String email;
    private String fullName;
    private String university;
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
    private String role;
    private BigDecimal walletBalance;
    private Boolean isVerified;
    private Boolean isAvailable;
    private Boolean isBanned;
    private LocalDate dateOfBirth;
    private Integer age;
    private Double averageRatingAsFreelancer;
    private Double averageRatingAsHirer;
    private Long totalReviewsAsFreelancer;
    private Long totalReviewsAsHirer;
    private BigDecimal totalEarnings;
    private Long completedTasksAsFreelancer;
    private Long completedTasksAsHirer;
    private String memberSince;
    private Role roleEnum;
    private Set<Role> roles;
    private boolean emailVerified;
    private LocalDateTime createdAt;
}
