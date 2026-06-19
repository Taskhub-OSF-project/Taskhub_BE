package com.taskhub.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ConversationResponse {
    private Long id;
    private Long taskId;
    private String taskTitle;
    private Long participantAId;
    private String participantAName;
    private Long participantBId;
    private String participantBName;
    private Long otherUserId;
    private String otherUserName;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private int unreadCount;
    private LocalDateTime createdAt;
}
