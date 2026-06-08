package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body để hirer mở dispute khi task ở trạng thái SUBMITTED.
 */
@Data
public class DisputeRequest {

    @NotBlank(message = "reason is required")
    @Size(max = 500, message = "reason must not exceed 500 characters")
    private String reason;

    @Size(max = 3000, message = "description must not exceed 3000 characters")
    private String description;
}
