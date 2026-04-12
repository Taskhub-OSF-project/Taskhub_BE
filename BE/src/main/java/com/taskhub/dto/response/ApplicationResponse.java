package com.taskhub.dto.response;

import com.taskhub.enums.ApplicationStatus;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ApplicationResponse {
    private UUID id;
    private UUID taskId;
    private UUID studentId;
    private String studentName;
    private String coverLetter;
    private ApplicationStatus status;
    private LocalDateTime appliedAt;
}
