package com.taskhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevisionRequestResponse {
    private Long id;
    private Long taskId;
    private Long submissionId;
    private Long requestedById;
    private Long studentId;
    private Integer revisionNumber;
    private String reason;
    private String description;
    private List<RevisionSuggestionResponse> aiSuggestions;
    private LocalDateTime createdAt;
}
