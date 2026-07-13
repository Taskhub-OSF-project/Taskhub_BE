package com.taskhub.dto.response;

import com.taskhub.dto.SubmittedFileDto;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SubmissionResponse {
    private Long id;
    private Long taskId;
    private Long studentId;
    private String studentName;
    private String fileUrl;
    private List<SubmittedFileDto> submittedFiles;
    private String notes;
    private Integer aiScore;
    private String aiReport;

    /** Evaluation fields */
    private Double finalScore;
    private Integer finalStars;
    private String finalRating;
    private String finalAssessment;
    private Boolean hirerOverridden;
    private LocalDateTime evaluatedAt;

    private Boolean isRevision;
    private LocalDateTime submittedAt;
}
