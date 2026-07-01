package com.taskhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProgressResponse {
    private Long taskId;
    private String taskTitle;
    private String currentStatus;
    private String progressSummary;
    private String aiAnalysis; // AI's assessment of the current state
    private String riskFlags; // potential issues AI identified
    private String recommendations;
    private LocalDateTime analyzedAt;
}
