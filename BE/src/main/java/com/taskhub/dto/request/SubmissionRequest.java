package com.taskhub.dto.request;

import com.taskhub.dto.SubmittedFileDto;
import lombok.*;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SubmissionRequest {
    private String fileUrl;
    private String notes;
    private List<SubmittedFileDto> submittedFiles;
}
