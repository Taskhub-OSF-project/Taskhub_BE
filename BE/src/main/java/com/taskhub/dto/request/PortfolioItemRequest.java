package com.taskhub.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PortfolioItemRequest {
    @NotBlank @Size(max = 255)
    private String title;

    private String description;

    @Size(max = 500)
    private String projectUrl;

    private List<String> imageUrls;

    private String fileUrl;

    @Size(max = 255)
    private String fileName;

    private Integer displayOrder;

    private Boolean isPublic;
}
