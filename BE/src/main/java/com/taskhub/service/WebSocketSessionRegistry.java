package com.taskhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.taskhub.security.JwtService;
import com.taskhub.repository.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketSessionRegistry extends TextWebSocketHandler implements SubProtocolCapable {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();

    @Value("${app.auth.require-email-verification:false}")
    private boolean requireEmailVerification;

    @Override
    public List<String> getSubProtocols() {
        return List.of("taskhub");
    }

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
        log.debug("WebSocket text message received on session {} ({} bytes)",
                session.getId(), message.getPayloadLength());
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
        String token = bearerToken(session.getHandshakeHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            for (String header : session.getHandshakeHeaders().getOrEmpty("Sec-WebSocket-Protocol")) {
                for (String protocol : header.split(",")) {
                    String candidate = protocol.trim();
                    if (!candidate.equals("taskhub") && candidate.split("\\.").length == 3) {
                        token = candidate;
                        break;
                    }
                }
                if (token != null) break;
            }
        }
        if (token == null || !jwtService.validateToken(token)
                || !JwtService.TYPE_ACCESS.equals(jwtService.getTokenType(token))) return null;
        Long userId = jwtService.getUserIdFromToken(token);
        return userRepository.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getIsBanned()))
                .filter(user -> !requireEmailVerification || user.isEmailVerified())
                .map(user -> user.getId())
                .orElse(null);
    }

    private String bearerToken(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim()
                : null;
    }
}
