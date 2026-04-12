package com.taskhub.dto.response;

import com.taskhub.enums.CriteriaStatus;
import lombok.*;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CriteriaResponse {
    private UUID id;
    private String description;
    private CriteriaStatus status;
}
