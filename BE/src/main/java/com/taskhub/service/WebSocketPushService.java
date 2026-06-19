package com.taskhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketPushService {
    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public void pushToUser(Long userId, String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String message = eventType + ":" + json;
            sessionRegistry.sendToUser(userId, message);
        } catch (Exception e) {
            log.warn("Failed to push {} to user {}: {}", eventType, userId, e.getMessage());
        }
    }
}
