package com.taskhub.dto.response;

import com.taskhub.enums.ApplicationStatus;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ApplicationResponse {
    private Long id;
    private Long taskId;
    private Long studentId;
    private String studentName;
    private String coverLetter;
    private ApplicationStatus status;
    private LocalDateTime appliedAt;
}
