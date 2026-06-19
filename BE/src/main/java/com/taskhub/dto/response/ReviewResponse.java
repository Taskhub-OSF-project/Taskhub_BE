package com.taskhub.dto.response;

import com.taskhub.enums.ReviewType;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewResponse {
    private Long id;
    private Long taskId;
    private String taskTitle;
    private Long reviewerId;
    private String reviewerName;
    private Long revieweeId;
    private String revieweeName;
    private ReviewType type;
    private Integer rating;
    private String comment;
    private Boolean isPublic;
    private LocalDateTime createdAt;
}
