package com.taskhub.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastNotificationResponse {
    private int recipients;
    private String targetRole;
}
