package com.mindspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindspace.model.ConversationMember;
import com.mindspace.model.User;
import com.mindspace.repository.ConversationMemberRepository;
import com.mindspace.repository.UserRepository;
import com.mindspace.ws.ChatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fans a user's online / last-seen status out to the people they chat with, over the
 * existing chat WebSocket. Presence is opt-out per user ({@code activityVisible}); a
 * user who has hidden their status is never advertised. Displaying others' status is
 * additionally gated client-side, so hiding yours also hides theirs from you (mutual).
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final ConversationMemberRepository memberRepo;
    private final UserRepository userRepository;
    private final ChatWebSocketHandler wsHandler;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    // @Lazy breaks the constructor cycle: the WS handler depends on this service and
    // this service pushes back through the handler.
    public PresenceService(ConversationMemberRepository memberRepo,
                           UserRepository userRepository,
                           @Lazy ChatWebSocketHandler wsHandler) {
        this.memberRepo = memberRepo;
        this.userRepository = userRepository;
        this.wsHandler = wsHandler;
    }

    /** A user's first socket opened — tell their contacts they're active. */
    @Transactional(readOnly = true)
    public void onConnect(UUID userId) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null || !u.isActivityVisible()) return;
        broadcast(u, true, null);
    }

    /** A user's last socket closed — stamp last-seen and tell their contacts. */
    @Transactional
    public void onDisconnect(UUID userId) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null) return;
        LocalDateTime now = LocalDateTime.now();
        u.setLastSeenAt(now);
        userRepository.save(u);
        if (!u.isActivityVisible()) return;
        broadcast(u, false, now);
    }

    /** Client toggled their "show my activity" preference on the live socket. */
    @Transactional
    public void setVisibility(UUID userId, boolean visible) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null || u.isActivityVisible() == visible) return;
        u.setActivityVisible(visible);
        userRepository.save(u);
        if (visible) {
            broadcast(u, wsHandler.isOnline(userId), null);
        } else {
            // Immediately disappear for contacts: report offline with the last known time.
            broadcast(u, false, u.getLastSeenAt());
        }
    }

    // Distinct other users who share at least one conversation with `user`.
    private Set<UUID> contactsOf(User user) {
        Set<UUID> out = new HashSet<>();
        for (ConversationMember mine : memberRepo.findByUser(user)) {
            for (ConversationMember peer : memberRepo.findByConversation(mine.getConversation())) {
                UUID id = peer.getUser().getId();
                if (!id.equals(user.getId())) out.add(id);
            }
        }
        return out;
    }

    private void broadcast(User subject, boolean online, LocalDateTime lastSeen) {
        String json;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "presence");
            payload.put("userId", subject.getId().toString());
            payload.put("online", online);
            if (lastSeen != null) payload.put("lastSeen", lastSeen);
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.debug("presence serialize failed: {}", e.getMessage());
            return;
        }
        for (UUID contactId : contactsOf(subject)) {
            wsHandler.sendToUser(contactId, json);
        }
    }
}
