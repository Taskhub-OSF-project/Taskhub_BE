package com.taskhub.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MessageResponse {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderName;
    private String content;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
