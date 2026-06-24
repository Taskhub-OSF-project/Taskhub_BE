package com.taskhub.dto.request;

import com.taskhub.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BroadcastNotificationRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 5000)
    private String body;

    /** Optional role filter: HIRER, STUDENT, or null = all (admins excluded). */
    private String targetRole;

    private NotificationType type;

    @Size(max = 300)
    private String link;

    private Long relatedId;
}