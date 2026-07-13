package com.taskhub.dto.request;

import com.taskhub.enums.RemovalReason;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRemovalRequestDto {
    @NotNull(message = "Lý do gỡ job là bắt buộc")
    private RemovalReason reason;

    private String reasonDescription;
}
