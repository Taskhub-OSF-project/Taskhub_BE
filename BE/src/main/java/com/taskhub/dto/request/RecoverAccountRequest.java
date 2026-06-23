package com.taskhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecoverAccountRequest {
    @NotBlank
    private String channel;
    @NotBlank
    private String contact;
}
