package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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

    /** Stable key for an unsaved task draft, for example "new-task". */
    @Size(max = 80)
    private String contextKey;

    @Valid
    private AiTaskDraftContext taskDraft;

    private String sessionType; // CHAT, EVALUATION, CRITERIA, DISPUTE, PROGRESS

    @NotBlank(message = "Message is required")
    @Size(max = 2000, message = "Message is too long")
    private String message;

    private String attachmentUrl;
}
