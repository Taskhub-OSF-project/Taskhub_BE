package com.taskhub.controller;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.SecurityEventResponse;
import com.taskhub.entity.SecurityEvent;
import com.taskhub.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {
    private final AuditService auditService;

    @GetMapping("/security-events")
    public ResponseEntity<ApiResponse<?>> getSecurityEvents(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequestDto pageReq = PageRequestDto.builder()
                .page(page)
                .size(Math.min(size, 100))
                .build();

        if (userId != null) {
            return ResponseEntity.ok(ApiResponse.ok("Security events retrieved",
                    auditService.getSecurityEventsByUser(userId, pageReq)));
        }

        if (eventType != null && !eventType.isBlank()) {
            try {
                SecurityEvent.EventType type = SecurityEvent.EventType.valueOf(eventType.toUpperCase());
                return ResponseEntity.ok(ApiResponse.ok("Security events retrieved",
                        auditService.getSecurityEventsByType(type, pageReq)));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.ok(ApiResponse.ok("Security events retrieved",
                        auditService.getSecurityEvents(pageReq)));
            }
        }

        return ResponseEntity.ok(ApiResponse.ok("Security events retrieved",
                auditService.getSecurityEvents(pageReq)));
    }
}
