package com.mindspace.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindspace.model.User;
import com.mindspace.repository.UserRepository;
import com.mindspace.security.JwtUtil;
import com.mindspace.service.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Presence + live delivery for member messaging. Each connection authenticates with a
 * JWT in the query string and registers under the user's id, so {@link #sendToUser}
 * can push new messages to every device that user has open. Sending happens over REST;
 * this socket only fans out already-persisted messages.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // userId -> that user's open sockets (multiple tabs/devices).
    private final Map<UUID, CopyOnWriteArrayList<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    // @Lazy avoids a construction cycle with PresenceService, which pushes back through this handler.
    public ChatWebSocketHandler(JwtUtil jwtUtil, UserRepository userRepository,
                                @Lazy PresenceService presenceService) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.presenceService = presenceService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = queryParams(session.getUri()).get("token");
        UUID userId;
        try {
            if (token == null || jwtUtil.isDeviceToken(token) || !jwtUtil.isTokenValid(token)) {
                throw new IllegalArgumentException("invalid token");
            }
            User user = userRepository.findByEmail(jwtUtil.extractEmail(token))
                    .orElseThrow(() -> new IllegalArgumentException("unknown user"));
            userId = user.getId();
        } catch (Exception e) {
            send(session, "{\"type\":\"error\",\"message\":\"unauthorized\"}");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.getAttributes().put("userId", userId);
        CopyOnWriteArrayList<WebSocketSession> list =
                sessions.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        boolean wasOffline = list.isEmpty();
        list.add(session);
        send(session, "{\"type\":\"ready\"}");
        // Only announce presence when this is the user's first live socket.
        UUID uid = userId;
        if (wasOffline) safelyRun(() -> presenceService.onConnect(uid));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID userId = (UUID) session.getAttributes().get("userId");
        if (userId == null) return;
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            if ("presence-visibility".equals(node.path("type").asText())) {
                presenceService.setVisibility(userId, node.path("visible").asBoolean(true));
            }
            // Other inbound frames (e.g. heartbeats) need no relay — messages go over REST.
        } catch (Exception e) {
            log.debug("chat ws inbound parse failed: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = (UUID) session.getAttributes().get("userId");
        if (userId == null) return;
        CopyOnWriteArrayList<WebSocketSession> list = sessions.get(userId);
        boolean nowOffline = false;
        if (list != null) {
            list.remove(session);
            if (list.isEmpty()) {
                sessions.remove(userId);
                nowOffline = true;
            }
        }
        if (nowOffline) safelyRun(() -> presenceService.onDisconnect(userId));
    }

    // Presence fan-out touches the DB and must never break the socket lifecycle.
    private void safelyRun(Runnable r) {
        try { r.run(); } catch (Exception e) { log.debug("presence hook failed: {}", e.getMessage()); }
    }

    /** True if the user has at least one live socket open. */
    public boolean isOnline(UUID userId) {
        CopyOnWriteArrayList<WebSocketSession> list = sessions.get(userId);
        if (list == null) return false;
        list.removeIf(s -> !s.isOpen());
        return !list.isEmpty();
    }

    /** Push a JSON payload to every socket the user has open. */
    public void sendToUser(UUID userId, String json) {
        CopyOnWriteArrayList<WebSocketSession> list = sessions.get(userId);
        if (list == null) return;
        for (WebSocketSession s : list) send(s, json);
    }

    private void send(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.debug("chat ws send failed: {}", e.getMessage());
        }
    }

    private Map<String, String> queryParams(URI uri) {
        Map<String, String> map = new java.util.HashMap<>();
        if (uri == null || uri.getQuery() == null) return map;
        for (String pair : uri.getQuery().split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                map.put(java.net.URLDecoder.decode(pair.substring(0, i), java.nio.charset.StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(pair.substring(i + 1), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return map;
    }
}
