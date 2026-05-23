package com.taskhub.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RevisionRequest {
    @NotEmpty
    private List<Long> failedCriteriaIds;
    private String feedback;
}
