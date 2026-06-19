package com.taskhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.taskhub.security.JwtService;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketSessionRegistry extends TextWebSocketHandler {
    private final JwtService jwtService;
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap();
    private final Map<String, Long> sessionUserMap = new ConcurrentHashMap();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        Long userId = extractUserId(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            log.warn("WebSocket connection rejected: invalid token");
            return;
        }
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionUserMap.put(session.getId(), userId);
        log.info("WebSocket connected: userId={}, session={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        log.debug("WebSocket text message received on session {}: {}", session.getId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        Long userId = sessionUserMap.remove(session.getId());
        if (userId != null) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) userSessions.remove(userId);
            }
            log.info("WebSocket disconnected: userId={}, session={}", userId, session.getId());
        }
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) throws Exception {
        log.error("WebSocket transport error on session {}: {}", session.getId(), exception.getMessage());
    }

    public void sendToUser(Long userId, String payload) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) return;
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (Exception e) {
                    log.warn("Failed to send WebSocket message to session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    public void broadcast(String payload) {
        TextMessage message = new TextMessage(payload);
        for (Set<WebSocketSession> sessions : userSessions.values()) {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (Exception e) {
                        log.warn("Broadcast failed for session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        }
    }

    private Long extractUserId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query == null) return null;
        String token = null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                token = kv[1];
                break;
            }
        }
        if (token == null || !jwtService.validateToken(token)) return null;
        return jwtService.getUserIdFromToken(token);
    }
}