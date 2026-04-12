package com.taskhub.dto.response;

import com.taskhub.enums.TaskStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TaskResponse {
    private UUID id;
    private String title;
    private String description;
    private BigDecimal budget;
    private LocalDateTime deadline;
    private TaskStatus status;
    private UUID hirerId;
    private String hirerName;
    private UUID assignedToId;
    private String assignedToName;
    private List<CriteriaResponse> acceptanceCriteria;
    private LocalDateTime createdAt;
}
