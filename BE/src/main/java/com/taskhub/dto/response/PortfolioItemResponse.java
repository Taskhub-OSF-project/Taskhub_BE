package com.taskhub.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PortfolioItemResponse {
    private Long id;
    private Long userId;
    private String userFullName;
    private String title;
    private String description;
    private String projectUrl;
    private List<String> imageUrls;
    private String fileUrl;
    private String fileName;
    private Integer displayOrder;
    private Boolean isPublic;
    private Double averageRating;
    private Long totalReviews;
    private BigDecimal totalEarnings;
    private Long completedTasks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}