package com.taskhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
    private Long messageId;
    private Long sessionId;
    private String reply;
    private String sessionType;
    private LocalDateTime timestamp;

    // For structured responses (evaluation, criteria, etc.)
    private String responseType; // TEXT, CRITERIA_LIST, EVALUATION_RESULT, DISPUTE_RESOLUTION, PROGRESS_REPORT
    private Object structuredData; // contains criteria list, evaluation scores, etc.
    private List<String> suggestedCriteria;
}
