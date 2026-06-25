package com.taskhub.controller;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.request.SendMessageRequest;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.ConversationResponse;
import com.taskhub.dto.response.MessageResponse;
import com.taskhub.service.MessagingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messaging")
@RequiredArgsConstructor
public class MessagingController {
    private final MessagingService messagingService;

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> conversations() {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.getMyConversationsList()));
    }

    @GetMapping("/conversations/paged")
    public ResponseEntity<ApiResponse<PageResponse<ConversationResponse>>> conversationsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequestDto pageReq = PageRequestDto.builder().page(page).size(size).build();
        return ResponseEntity.ok(ApiResponse.ok(messagingService.getMyConversations(pageReq)));
    }

    @PostMapping("/conversations/task/{taskId}")
    public ResponseEntity<ApiResponse<ConversationResponse>> getOrCreateConversation(@PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.getOrCreateConversation(taskId)));
    }

    @PostMapping("/conversations/task/{taskId}/user/{userId}")
    public ResponseEntity<ApiResponse<ConversationResponse>> getOrCreateConversationWithUser(
            @PathVariable Long taskId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.getOrCreateConversation(taskId, userId)));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Message sent", messagingService.sendMessage(conversationId, req)));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> getMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequestDto pageReq = PageRequestDto.builder().page(page).size(size).build();
        return ResponseEntity.ok(ApiResponse.ok(messagingService.getMessages(conversationId, pageReq)));
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long conversationId) {
        messagingService.markAsRead(conversationId);
        return ResponseEntity.ok(ApiResponse.ok("Marked as read", null));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        long count = messagingService.getTotalUnreadCount();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", count)));
    }
}
