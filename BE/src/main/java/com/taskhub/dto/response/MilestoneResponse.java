package com.taskhub.dto.response;

import com.taskhub.enums.EscrowStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MilestoneResponse {
    private Long id;
    private Long taskId;
    private String title;
    private String description;
    private BigDecimal amount;
    private LocalDateTime dueDate;
    private Integer displayOrder;
    private String status;
    private String escrowStatus;
    private LocalDateTime fundedAt;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;
}
