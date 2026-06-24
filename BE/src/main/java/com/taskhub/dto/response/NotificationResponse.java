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
    private String link;
    private Long relatedId;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .link(n.getLink())
                .relatedId(n.getRelatedId())
                .isRead(n.getIsRead())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}