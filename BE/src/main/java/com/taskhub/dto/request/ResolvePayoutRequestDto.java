package com.taskhub.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResolvePayoutRequestDto {
    @NotNull(message = "Quyết định duyệt (approved) là bắt buộc")
    private Boolean approved;
    
    private String note;
}
