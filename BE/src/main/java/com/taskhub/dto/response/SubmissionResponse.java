package com.taskhub.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SubmissionResponse {
    private UUID id;
    private UUID taskId;
    private UUID studentId;
    private String studentName;
    private String fileUrl;
    private String notes;
    private Integer aiScore;
    private String aiReport;
    private Boolean isRevision;
    private LocalDateTime submittedAt;
}
