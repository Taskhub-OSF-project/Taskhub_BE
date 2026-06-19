package com.taskhub.dto.response;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PublicTaskResponse {
    private Long id;
    private String title;
    private String description;
    private String category;
    private String budget;
    private String deadline;
    private String status;
    private String hirerName;
    private Long hirerId;
    private String hirerAvatarUrl;
    private List<String> skillsRequired;
    private int applicantCount;
    private String createdAt;
}
