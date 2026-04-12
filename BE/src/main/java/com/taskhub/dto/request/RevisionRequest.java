package com.taskhub.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RevisionRequest {
    @NotEmpty
    private List<UUID> failedCriteriaIds;
    private String feedback;
}
