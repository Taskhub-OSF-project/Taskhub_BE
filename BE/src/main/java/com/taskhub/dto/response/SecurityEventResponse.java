package com.taskhub.dto.response;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SecurityEventResponse {
    private Long id;
    private Long userId;
    private String userEmailHash;
    private String eventType;
    private String outcome;
    private String ipAddress;
    private String userAgent;
    private String reason;
    private String metadata;
    private String createdAt;
}
