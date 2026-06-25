package com.taskhub.controller;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.NotificationResponse;
import com.taskhub.security.AuthUtil;
import com.taskhub.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequestDto req = PageRequestDto.builder().page(page).size(size).build();
        return ResponseEntity.ok(ApiResponse.ok("Notifications retrieved",
                notificationService.listForUser(AuthUtil.getCurrentUser().getId(), req)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        long count = notificationService.unreadCount(AuthUtil.getCurrentUser().getId());
        return ResponseEntity.ok(ApiResponse.ok("Unread count", Map.of("count", count)));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCountAlias() {
        return unreadCount();
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> unread() {
        return ResponseEntity.ok(ApiResponse.ok("Unread notifications retrieved",
                notificationService.unreadForUser(AuthUtil.getCurrentUser().getId())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long id) {
        notificationService.markRead(id, AuthUtil.getCurrentUser().getId());
        return ResponseEntity.ok(ApiResponse.ok("Notification marked as read", null));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markReadPost(@PathVariable Long id) {
        return markRead(id);
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead() {
        int updated = notificationService.markAllRead(AuthUtil.getCurrentUser().getId());
        return ResponseEntity.ok(ApiResponse.ok("All notifications marked as read",
                Map.of("updated", updated)));
    }
}
