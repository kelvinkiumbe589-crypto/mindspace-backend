package com.mindspace.ws;

import com.mindspace.service.CallSessionService;
import com.mindspace.service.SessionRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebRTC signaling relay for 1:1 online sessions. The server authorizes the
 * connection (JWT + booking membership) and then blindly relays SDP/ICE messages
 * between the (at most) two peers in a booking's room — media never touches us.
 */
@Component
public class SessionSignalingHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SessionSignalingHandler.class);

    private final SessionRoomService roomService;
    private final CallSessionService callService;

    // bookingId -> the (max 2) connected peers in that room.
    private final Map<String, CopyOnWriteArrayList<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    public SessionSignalingHandler(SessionRoomService roomService, CallSessionService callService) {
        this.roomService = roomService;
        this.callService = callService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> q = queryParams(session.getUri());
        String bookingId = q.get("bookingId");
        String token = q.get("token");

        SessionRoomService.Participant p;
        try {
            p = roomService.authorizeToken(token, UUID.fromString(bookingId));
        } catch (Exception e) {
            send(session, "{\"type\":\"error\",\"message\":\"unauthorized\"}");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        session.getAttributes().put("bookingId", bookingId);
        session.getAttributes().put("role", p.role());

        CopyOnWriteArrayList<WebSocketSession> room = rooms.computeIfAbsent(bookingId, k -> new CopyOnWriteArrayList<>());
        // Drop any stale/closed sessions before enforcing the 2-peer cap.
        room.removeIf(s -> !s.isOpen());
        if (room.size() >= 2) {
            send(session, "{\"type\":\"full\"}");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        room.add(session);

        // Tell the newcomer they're in; tell the peer already present to start the offer.
        send(session, "{\"type\":\"joined\"}");
        for (WebSocketSession other : room) {
            if (other != session && other.isOpen()) {
                send(other, "{\"type\":\"peer-joined\"}");
            }
        }

        // Both parties are now present — start charging the connected-time budget.
        if (room.size() == 2) {
            safely(() -> callService.onBothConnected(bookingId));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String bookingId = (String) session.getAttributes().get("bookingId");
        if (bookingId == null) return;
        CopyOnWriteArrayList<WebSocketSession> room = rooms.get(bookingId);
        if (room == null) return;
        // Relay verbatim to the other peer(s).
        for (WebSocketSession other : room) {
            if (other != session && other.isOpen()) {
                other.sendMessage(new TextMessage(message.getPayload()));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String bookingId = (String) session.getAttributes().get("bookingId");
        if (bookingId == null) return;
        CopyOnWriteArrayList<WebSocketSession> room = rooms.get(bookingId);
        if (room == null) return;
        room.remove(session);
        for (WebSocketSession other : room) {
            if (other.isOpen()) send(other, "{\"type\":\"peer-left\"}");
        }
        // No longer a full room — bank the elapsed connected time (idempotent).
        if (room.size() < 2) {
            safely(() -> callService.onRoomDrain(bookingId));
        }
        if (room.isEmpty()) rooms.remove(bookingId);
    }

    /**
     * Force-close a room (both peers), first telling them why (e.g. "time-up").
     * Called from the budget scheduler when the paid call time runs out.
     */
    public void closeRoom(String bookingId, String reason) {
        CopyOnWriteArrayList<WebSocketSession> room = rooms.get(bookingId);
        if (room == null) return;
        for (WebSocketSession s : room) {
            send(s, "{\"type\":\"" + reason + "\"}");
            try { if (s.isOpen()) s.close(CloseStatus.NORMAL); } catch (Exception ignored) {}
        }
    }

    // DB-touching budget hooks must never break the socket lifecycle.
    private void safely(Runnable r) {
        try { r.run(); } catch (Exception e) { log.debug("call budget hook failed: {}", e.getMessage()); }
    }

    private void send(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.debug("signaling send failed: {}", e.getMessage());
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
