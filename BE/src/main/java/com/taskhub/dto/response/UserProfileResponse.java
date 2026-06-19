package com.taskhub.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

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
    private Double averageRatingAsFreelancer;
    private Double averageRatingAsHirer;
    private Long totalReviewsAsFreelancer;
    private Long totalReviewsAsHirer;
    private BigDecimal totalEarnings;
    private Long completedTasksAsFreelancer;
    private Long completedTasksAsHirer;
    private String memberSince;
}