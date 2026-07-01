package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    private Long sessionId;

    private Long taskId;

    private Long submissionId;

    private String sessionType; // CHAT, EVALUATION, CRITERIA, DISPUTE, PROGRESS

    @NotBlank(message = "Message is required")
    private String message;

    private String attachmentUrl;
}
