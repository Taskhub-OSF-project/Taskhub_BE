package com.taskhub.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRemovalResolveDto {
    @NotNull(message = "Phê duyệt là bắt buộc")
    private Boolean approved;

    private String adminNotes;
}
