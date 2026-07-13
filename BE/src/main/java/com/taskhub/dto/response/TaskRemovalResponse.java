package com.taskhub.dto.response;

import com.taskhub.enums.RemovalReason;
import com.taskhub.enums.RemovalStatus;
import com.taskhub.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRemovalResponse {
    private Long id;
    private Long taskId;
    private String taskTitle;
    private TaskStatus taskStatus;
    private Long requestedById;
    private String requestedByName;
    private RemovalReason reason;
    private String reasonLabel;
    private String reasonDescription;
    private TaskStatus taskStatusAtRequest;
    private RemovalStatus status;
    private String statusLabel;
    private String aiValidationResult;
    private String aiRecommendation;
    private Long adminId;
    private String adminName;
    private String adminNotes;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}
