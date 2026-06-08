package com.taskhub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmittedFileDto {
    private String fileName;
    private String path;
    private String url;
    private String contentType;
    private Long size;
    private LocalDateTime uploadedAt;
}
