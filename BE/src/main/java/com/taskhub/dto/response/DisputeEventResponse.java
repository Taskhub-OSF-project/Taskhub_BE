package com.taskhub.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DisputeEventResponse {
    private Long id;
    private Long taskId;
    private String eventType;
    private Long performedBy;
    private String performedByRole;
    private String details;
    private String aiRecommendation;
    private String actionTaken;
    private LocalDateTime createdAt;
}
