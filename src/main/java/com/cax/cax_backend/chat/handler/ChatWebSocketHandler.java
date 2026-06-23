package com.cax.cax_backend.chat.handler;

import com.cax.cax_backend.chat.model.ClubMessage;
import com.cax.cax_backend.chat.service.ChatService;
import com.cax.cax_backend.common.util.JwtUtil;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;
    private final JwtUtil jwtUtil;
    private final ChatSessionTracker chatSessionTracker;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Map clubId -> active WebSocket sessions in this club
    private final Map<String, Set<WebSocketSession>> clubSessions = new ConcurrentHashMap<>();
    // Map sessionId -> clubId
    private final Map<String, String> sessionClubMap = new ConcurrentHashMap<>();
    // Map sessionId -> userId
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received WS message: {}", payload);

        try {
            Map<String, String> data = objectMapper.readValue(payload, Map.class);
            String type = data.get("type");
            String clubId = data.get("clubId");

            if ("JOIN".equals(type) && clubId != null) {
                String userId = extractUserId(session, data);
                if (userId == null) {
                    log.warn("WS JOIN request rejected: unable to authenticate user");
                    session.close(CloseStatus.NOT_ACCEPTABLE);
                    return;
                }

                // Check if user is blocked
                boolean isBlocked = userRepository.findByUserId(userId)
                        .map(User::isBlocked)
                        .orElse(false);
                if (isBlocked) {
                    log.warn("WS JOIN request rejected: user {} is blocked", userId);
                    session.close(CloseStatus.NOT_ACCEPTABLE);
                    return;
                }

                // Associate user and club with session
                clubSessions.computeIfAbsent(clubId, k -> ConcurrentHashMap.newKeySet()).add(session);
                sessionClubMap.put(session.getId(), clubId);
                sessionUserMap.put(session.getId(), userId);
                session.getAttributes().put("isBlocked", isBlocked);
                chatSessionTracker.registerSession(userId, clubId, session.getId());
                
                log.info("User {} joined WebSocket channel for club {}", userId, clubId);

                // Send confirmation
                Map<String, String> response = Map.of("type", "JOIN_ACK", "status", "SUCCESS");
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            } else if ("MESSAGE".equals(type) && clubId != null) {
                String senderId = sessionUserMap.get(session.getId());
                if (senderId == null) {
                    // Try to authenticate on the fly
                    senderId = extractUserId(session, data);
                    if (senderId == null) {
                        session.close(CloseStatus.NOT_ACCEPTABLE);
                        return;
                    }
                    sessionUserMap.put(session.getId(), senderId);
                }

                // Check if user is blocked (using session attributes to prevent redundant DB hits)
                Boolean isBlocked = (Boolean) session.getAttributes().get("isBlocked");
                if (isBlocked == null) {
                    isBlocked = userRepository.findByUserId(senderId)
                            .map(User::isBlocked)
                            .orElse(false);
                    session.getAttributes().put("isBlocked", isBlocked);
                }
                if (isBlocked) {
                    log.warn("WS MESSAGE request rejected: user {} is blocked", senderId);
                    session.close(CloseStatus.NOT_ACCEPTABLE);
                    return;
                }

                String content = data.get("content");
                if (content == null || content.trim().isEmpty()) {
                    return;
                }

                String replyToId = data.get("replyToId");
                String replyToName = data.get("replyToName");
                String replyToContent = data.get("replyToContent");

                // Save message in DB and trigger notifications
                ClubMessage savedMessage = chatService.sendMessage(senderId, clubId, content, replyToId, replyToName, replyToContent);

                // Broadcast to all active sessions in the club
                broadcastToClub(clubId, savedMessage);
            }
        } catch (Exception e) {
            log.error("Error processing WS message: {}", e.getMessage(), e);
        }
    }

    private String extractUserId(WebSocketSession session, Map<String, String> payloadData) {
        try {
            // 1. Try from headers
            List<String> authHeaders = session.getHandshakeHeaders().get("Authorization");
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String header = authHeaders.get(0);
                if (header.startsWith("Bearer ")) {
                    String token = header.substring(7);
                    return jwtUtil.extractUserId(token);
                }
            }

            // 2. Try from query parameters
            URI uri = session.getUri();
            if (uri != null && uri.getQuery() != null) {
                String query = uri.getQuery();
                for (String param : query.split("&")) {
                    // limit=2 so base64-padded JWT values with '=' are not split mid-token
                    String[] pair = param.split("=", 2);
                    if (pair.length == 2 && "token".equals(pair[0])) {
                        return jwtUtil.extractUserId(pair[1]);
                    }
                }
            }

            // 3. Try from message payload
            if (payloadData != null && payloadData.containsKey("token")) {
                return jwtUtil.extractUserId(payloadData.get("token"));
            }

            // 4. Try from Principal
            if (session.getPrincipal() != null) {
                return session.getPrincipal().getName();
            }
        } catch (Exception e) {
            log.debug("Token validation in WS failed: {}", e.getMessage());
        }

        return null;
    }

    public void broadcastToClub(String clubId, ClubMessage message) {
        try {
            Map<String, Object> messageMap = new java.util.HashMap<>();
            messageMap.put("type", "NEW_MESSAGE");
            messageMap.put("id", message.getId());
            messageMap.put("clubId", message.getClubId());
            messageMap.put("senderId", message.getSenderId());
            messageMap.put("senderName", message.getSenderName());
            messageMap.put("senderPicture", message.getSenderPicture() != null ? message.getSenderPicture() : "");
            messageMap.put("content", message.getContent());
            messageMap.put("createdAt", message.getCreatedAt().toString());
            // Only include reply fields when present so receivers don't see spurious null keys
            if (message.getReplyToId() != null) messageMap.put("replyToId", message.getReplyToId());
            if (message.getReplyToName() != null) messageMap.put("replyToName", message.getReplyToName());
            if (message.getReplyToContent() != null) messageMap.put("replyToContent", message.getReplyToContent());
            String messageJson = objectMapper.writeValueAsString(messageMap);

            Set<WebSocketSession> sessions = clubSessions.getOrDefault(clubId, Collections.emptySet());
            log.debug("Broadcasting chat message to {} session(s) in club {}", sessions.size(), clubId);

            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) {
                    // Prune stale sessions discovered during broadcast
                    sessions.remove(session);
                    continue;
                }
                // Synchronize per-session: WebSocketSession.sendMessage is not thread-safe;
                // without this, concurrent broadcasts corrupt the WS frame.
                synchronized (session) {
                    try {
                        session.sendMessage(new TextMessage(messageJson));
                    } catch (IOException e) {
                        log.warn("Failed to send WS message to session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to broadcast message: {}", e.getMessage(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        String clubId = sessionClubMap.remove(sessionId);
        String userId = sessionUserMap.remove(sessionId);

        if (clubId != null) {
            Set<WebSocketSession> sessions = clubSessions.get(clubId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    clubSessions.remove(clubId, sessions);
                }
            }
        }
        if (userId != null && clubId != null) {
            chatSessionTracker.deregisterSession(userId, clubId, sessionId);
        }
        log.info("WebSocket connection closed for session {}", sessionId);
    }
}
