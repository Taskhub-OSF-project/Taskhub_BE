package com.taskhub.dto.response;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CriteriaExtractResponse {
    private String fileName;
    private String detectedType;
    private List<ExtractedCriterion> suggestions;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ExtractedCriterion {
        private String text;
        private String rationale;
    }
}
