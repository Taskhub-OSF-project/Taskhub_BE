package com.taskhub.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FileUploadResponse {
    private String fileName;
    private String path;
    private String url;
    private String contentType;
    private Long size;
    private LocalDateTime uploadedAt;
}
