package com.taskhub.dto.response;

import com.taskhub.enums.NotificationType;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private Boolean isRead;
    private LocalDateTime readAt;
    private String actionUrl;
    private Long taskId;
    private LocalDateTime createdAt;
}
