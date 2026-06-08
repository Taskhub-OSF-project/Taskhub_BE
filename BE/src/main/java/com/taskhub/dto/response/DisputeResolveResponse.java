package com.taskhub.dto.response;

import com.taskhub.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response sau khi hirer resolve dispute.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeResolveResponse {

    private Long taskId;
    private TaskStatus newStatus;
    /** Action đã thực hiện: RELEASE_PAYMENT | REQUEST_REVISION | ESCALATE */
    private String action;
    private String message;
    private LocalDateTime resolvedAt;
}
