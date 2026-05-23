package com.taskhub.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SubmissionResponse {
    private Long id;
    private Long taskId;
    private Long studentId;
    private String studentName;
    private String fileUrl;
    private String notes;
    private Integer aiScore;
    private String aiReport;
    private Boolean isRevision;
    private LocalDateTime submittedAt;
}
