package com.taskhub.dto.response;

import com.taskhub.entity.Notification;
import com.taskhub.enums.NotificationType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String body;
    private String message;
    private String link;
    private String actionUrl;
    private Long relatedId;
    private Long taskId;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .message(n.getBody())
                .link(n.getLink())
                .actionUrl(n.getLink())
                .relatedId(n.getRelatedId())
                .taskId(n.getRelatedId())
                .isRead(n.getIsRead())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
