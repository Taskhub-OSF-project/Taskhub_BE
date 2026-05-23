package com.taskhub.dto.response;

import com.taskhub.enums.CriteriaStatus;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CriteriaResponse {
    private Long id;
    private String description;
    private CriteriaStatus status;
}
