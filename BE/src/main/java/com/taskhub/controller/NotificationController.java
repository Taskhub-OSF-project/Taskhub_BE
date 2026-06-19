package com.taskhub.controller;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.NotificationResponse;
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
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequestDto pageReq = PageRequestDto.builder().page(page).size(size).build();
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getMyNotifications(pageReq)));
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnread() {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getUnreadNotifications()));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", notificationService.getUnreadCount())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.ok("Marked as read", null));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok(ApiResponse.ok("All marked as read", null));
    }
}
