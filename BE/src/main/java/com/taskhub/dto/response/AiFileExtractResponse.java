package com.taskhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFileExtractResponse {

    private String fileName;
    private String fileType;
    private String fileSize;
    private String purpose;

    private Map<String, Object> extractedData;

    private String summary;

    private String rawText;

    private Integer textLength;

    private String language;

    private String qualityScore;

    private LocalDateTime extractedAt;
}
