package com.taskhub.dto.response;

import com.taskhub.service.AiValidationService;
import lombok.*;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ValidationPhaseResponse {
    private String validationPhase; // "failed" | "passed"
    private String blockReason;
    private boolean canProceed;
    private String message;
    private List<AiValidationService.CriteriaSuggestion> suggestions;
    private TaskResponse taskResponse; // Nếu thành công
}
