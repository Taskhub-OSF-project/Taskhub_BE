package com.taskhub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.response.SecurityEventResponse;
import com.taskhub.entity.AuditLog;
import com.taskhub.entity.SecurityEvent;
import com.taskhub.entity.User;
import com.taskhub.repository.AuditLogRepository;
import com.taskhub.repository.SecurityEventRepository;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {
    private final SecurityEventRepository securityEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    // ── Security Events (async, non-blocking) ───────────────────────────────────

    @Async
    public void logSecurityEventAsync(SecurityEventParams params) {
        try {
            SecurityEvent event = SecurityEvent.builder()
                    .user(params.user)
                    .userEmailHash(params.userEmailHash)
                    .eventType(params.eventType)
                    .outcome(params.outcome)
                    .ipAddress(params.ipAddress)
                    .userAgent(params.userAgent)
                    .requestPath(params.requestPath)
                    .requestMethod(params.requestMethod)
                    .reason(params.reason)
                    .metadata(toJson(params.metadata))
                    .build();
            securityEventRepository.save(event);
        } catch (Exception e) {
            log.error("[AUDIT] Failed to persist security event {}: {}", params.eventType, e.getMessage());
        }
    }

    public void logSecurityEvent(SecurityEventParams params) {
        try {
            SecurityEvent event = SecurityEvent.builder()
                    .user(params.user)
                    .userEmailHash(params.userEmailHash)
                    .eventType(params.eventType)
                    .outcome(params.outcome)
                    .ipAddress(params.ipAddress)
                    .userAgent(params.userAgent)
                    .requestPath(params.requestPath)
                    .requestMethod(params.requestMethod)
                    .reason(params.reason)
                    .metadata(toJson(params.metadata))
                    .build();
            securityEventRepository.save(event);
        } catch (Exception e) {
            log.error("[AUDIT] Failed to persist security event {}: {}", params.eventType, e.getMessage());
        }
    }

    public void logLoginSuccess(User user, HttpServletRequest request) {
        logSecurityEvent(SecurityEventParams.builder()
                .user(user)
                .userEmailHash(hash(user.getEmail()))
                .eventType(SecurityEvent.EventType.LOGIN_SUCCESS)
                .outcome("SUCCESS")
                .ipAddress(resolveIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .requestPath(request.getRequestURI())
                .requestMethod(request.getMethod())
                .build());
    }

    public void logLoginFailure(String email, String reason, HttpServletRequest request) {
        logSecurityEvent(SecurityEventParams.builder()
                .userEmailHash(hash(email))
                .eventType(SecurityEvent.EventType.LOGIN_FAILURE)
                .outcome("FAILURE")
                .reason(reason)
                .ipAddress(resolveIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .requestPath(request.getRequestURI())
                .requestMethod(request.getMethod())
                .build());
    }

    public void logRegistration(User user, HttpServletRequest request) {
        logSecurityEvent(SecurityEventParams.builder()
                .user(user)
                .userEmailHash(hash(user.getEmail()))
                .eventType(SecurityEvent.EventType.REGISTRATION)
                .outcome("SUCCESS")
                .ipAddress(resolveIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .requestPath(request.getRequestURI())
                .requestMethod(request.getMethod())
                .build());
    }

    public void logPasswordResetRequest(String email, boolean found, HttpServletRequest request) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("accountFound", found);
        logSecurityEvent(SecurityEventParams.builder()
                .userEmailHash(hash(email))
                .eventType(SecurityEvent.EventType.PASSWORD_RESET_REQUEST)
                .outcome(found ? "SUCCESS" : "ACCOUNT_NOT_FOUND")
                .reason(found ? null : "Email not registered")
                .ipAddress(resolveIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .requestPath(request.getRequestURI())
                .requestMethod(request.getMethod())
                .metadata(meta)
                .build());
    }

    public void logPasswordResetSuccess(User user, HttpServletRequest request) {
        logSecurityEvent(SecurityEventParams.builder()
                .user(user)
                .userEmailHash(hash(user.getEmail()))
                .eventType(SecurityEvent.EventType.PASSWORD_RESET_SUCCESS)
                .outcome("SUCCESS")
                .ipAddress(resolveIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .requestPath(request.getRequestURI())
                .requestMethod(request.getMethod())
                .build());
    }

    public void logPasswordChange(Long userId, HttpServletRequest request) {
        logSecurityEvent(SecurityEventParams.builder()
                .userEmailHash("userId:" + userId)
                .eventType(SecurityEvent.EventType.PASSWORD_CHANGE)
                .outcome("SUCCESS")
                .ipAddress(resolveIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .requestPath(request.getRequestURI())
                .requestMethod(request.getMethod())
                .build());
    }

    // ── Audit Logs (business actions) ────────────────────────────────────────────

    public void log(AuditLogParams params) {
        try {
            AuditLog entry = AuditLog.builder()
                    .user(params.user)
                    .userEmailHash(params.userEmailHash)
                    .action(params.action)
                    .entityType(params.entityType)
                    .entityId(params.entityId)
                    .entityName(params.entityName)
                    .ipAddress(params.ipAddress)
                    .changes(toJson(params.changes))
                    .metadata(toJson(params.metadata))
                    .outcome(params.outcome != null ? params.outcome : "SUCCESS")
                    .failureReason(params.failureReason)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("[AUDIT] Failed to persist audit log {}: {}", params.action, e.getMessage());
        }
    }

    public void logEntityCreated(User user, AuditLog.AuditAction action,
            AuditLog.EntityType entityType, Long entityId, String entityName,
            HttpServletRequest request) {
        log(AuditLogParams.builder()
                .user(user)
                .userEmailHash(hash(user.getEmail()))
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .entityName(entityName)
                .ipAddress(resolveIp(request))
                .outcome("SUCCESS")
                .build());
    }

    // ── Admin Queries ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<SecurityEventResponse> getSecurityEvents(PageRequestDto pageReq) {
        PageRequest springPage = PageRequest.of(pageReq.getPage(), Math.min(pageReq.getSize(), 100));
        Page<SecurityEvent> page = securityEventRepository.findAll(springPage);
        return PageResponse.<SecurityEventResponse>builder()
                .content(page.getContent().stream().map(this::toSecurityEventResponse).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<SecurityEventResponse> getSecurityEventsByUser(Long userId, PageRequestDto pageReq) {
        PageRequest springPage = PageRequest.of(pageReq.getPage(), Math.min(pageReq.getSize(), 100));
        Page<SecurityEvent> page = securityEventRepository.findByUserIdOrderByCreatedAtDesc(userId, springPage);
        return toPageResponse(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<SecurityEventResponse> getSecurityEventsByType(
            SecurityEvent.EventType eventType, PageRequestDto pageReq) {
        PageRequest springPage = PageRequest.of(pageReq.getPage(), Math.min(pageReq.getSize(), 100));
        Page<SecurityEvent> page = securityEventRepository.findByEventTypeOrderByCreatedAtDesc(eventType, springPage);
        return toPageResponse(page);
    }

    private PageResponse<SecurityEventResponse> toPageResponse(Page<SecurityEvent> page) {
        return PageResponse.<SecurityEventResponse>builder()
                .content(page.getContent().stream().map(this::toSecurityEventResponse).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    private SecurityEventResponse toSecurityEventResponse(SecurityEvent e) {
        return SecurityEventResponse.builder()
                .id(e.getId())
                .userId(e.getUser() != null ? e.getUser().getId() : null)
                .userEmailHash(e.getUserEmailHash())
                .eventType(e.getEventType().name())
                .outcome(e.getOutcome())
                .ipAddress(e.getIpAddress())
                .userAgent(e.getUserAgent())
                .reason(e.getReason())
                .metadata(e.getMetadata())
                .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
                .build();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }

    private String hash(String value) {
        if (value == null) return null;
        try {
            return java.util.Base64.getEncoder().encodeToString(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.trim().toLowerCase().getBytes())
            ).substring(0, 16);
        } catch (Exception e) {
            return "***";
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return null; }
    }

    // ── Parameter builders ────────────────────────────────────────────────────────

    @lombok.Builder
    public record SecurityEventParams(
            User user,
            String userEmailHash,
            SecurityEvent.EventType eventType,
            String outcome,
            String ipAddress,
            String userAgent,
            String requestPath,
            String requestMethod,
            String reason,
            Map<String, Object> metadata
    ) {}

    @lombok.Builder
    public record AuditLogParams(
            User user,
            String userEmailHash,
            AuditLog.AuditAction action,
            AuditLog.EntityType entityType,
            Long entityId,
            String entityName,
            String ipAddress,
            Map<String, Object> changes,
            Map<String, Object> metadata,
            String outcome,
            String failureReason
    ) {}
}
