package com.taskhub.dto.response;

import com.taskhub.enums.TaskStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TaskResponse {
    private Long id;
    private String title;
    private String description;
    private BigDecimal budget;
    private LocalDateTime deadline;
    private TaskStatus status;
    private Long hirerId;
    private String hirerName;
    private Long assignedToId;
    private String assignedToName;
    private List<CriteriaResponse> acceptanceCriteria;
    private LocalDateTime createdAt;
}
