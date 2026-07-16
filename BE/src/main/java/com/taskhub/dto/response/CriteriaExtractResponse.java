package com.taskhub.dto.response;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CriteriaExtractResponse {
    private String fileName;
    private String detectedType;
    private String suggestedTitle;
    private String suggestedDescription;
    private String suggestedCategory;
    private Boolean logicallyConsistent;
    private String consistencySummary;
    private List<String> warnings;
    private List<ExtractedCriterion> suggestions;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ExtractedCriterion {
        private String text;
        private String rationale;
        private String sourceEvidence;
        private List<Integer> relatedCriteria;
    }
}
