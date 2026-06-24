package com.taskhub.dto.response;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SecurityEventResponse {
    private Long id;
    private Long userId;
    private String email;
    private String eventType;
    private String ipAddress;
    private String detail;
    private String createdAt;
}
