package com.mindspace.ws;

import com.mindspace.model.User;
import com.mindspace.repository.UserRepository;
import com.mindspace.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // userId -> that user's open sockets (multiple tabs/devices).
    private final Map<UUID, CopyOnWriteArrayList<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
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
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(session);
        send(session, "{\"type\":\"ready\"}");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client may send a heartbeat; nothing to relay — messages are sent over REST.
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = (UUID) session.getAttributes().get("userId");
        if (userId == null) return;
        CopyOnWriteArrayList<WebSocketSession> list = sessions.get(userId);
        if (list != null) {
            list.remove(session);
            if (list.isEmpty()) sessions.remove(userId);
        }
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
