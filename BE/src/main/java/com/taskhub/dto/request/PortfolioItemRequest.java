package com.taskhub.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PortfolioItemRequest {
    @NotBlank @Size(max = 255)
    private String title;

    @Size(max = 5000)
    private String description;

    @Size(max = 500)
    @Pattern(regexp = "(?i)^$|^https://[^\\s]+$", message = "Project URL must use HTTPS")
    private String projectUrl;

    @Size(max = 20)
    private List<@Pattern(regexp = "(?i)^https://[^\\s]+$") String> imageUrls;

    @Pattern(regexp = "(?i)^$|^https://[^\\s]+$", message = "File URL must use HTTPS")
    private String fileUrl;

    @Size(max = 255)
    private String fileName;

    private Integer displayOrder;

    private Boolean isPublic;
}
