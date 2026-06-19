package com.taskhub.dto.response;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FreelancerSearchResponse {
    private Long id;
    private String fullName;
    private String email;
    private String university;
    private String major;
    private String bio;
    private List<String> skills;
    private String experience;
    private String portfolioUrl;
    private String avatarUrl;
    private String availability;
    private List<String> languages;
    private List<String> certifications;
    private Double averageRating;
    private Long totalReviews;
    private Long completedTasks;
    private String memberSince;
}
