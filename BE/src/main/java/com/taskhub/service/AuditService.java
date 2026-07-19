package com.taskhub.service;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.response.SecurityEventResponse;
import com.taskhub.entity.SecurityEvent;
import com.taskhub.repository.SecurityEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditService {
    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private final SecurityEventRepository securityEventRepository;

    public void record(String event, String email, String detail) {
        String ip = clientIp();
        AUDIT.info("event={} email={} ip={} detail={}",
                event, mask(email), ip, detail == null ? "-" : detail);
        persistAsync(event, email, ip, detail);
    }

    public void record(String event, String email) {
        record(event, email, null);
    }

    @Async
    void persistAsync(String event, String email, String ip, String detail) {
        try {
            securityEventRepository.save(SecurityEvent.builder()
                    .eventType(event)
                    .email(email)
                    .ipAddress(ip)
                    .detail(detail)
                    .build());
        } catch (Exception ex) {
            AUDIT.warn("Failed to persist security event {}: {}", event, ex.getMessage());
        }
    }

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
    public PageResponse<SecurityEventResponse> getSecurityEventsByType(String eventType, PageRequestDto pageReq) {
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
                .userId(e.getUserId())
                .email(e.getEmail())
                .eventType(e.getEventType())
                .ipAddress(e.getIpAddress())
                .detail(e.getDetail())
                .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
                .build();
    }

    static String clientIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) return "-";
        HttpServletRequest req = servletAttrs.getRequest();
        return req.getRemoteAddr();
    }

    static String mask(String email) {
        if (email == null || email.isBlank()) return "-";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.charAt(0) + "***" + email.substring(at);
    }
}
