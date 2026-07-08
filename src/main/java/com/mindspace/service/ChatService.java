package com.mindspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindspace.dto.ChatDto;
import com.mindspace.model.ChatMessage;
import com.mindspace.model.Conversation;
import com.mindspace.model.ConversationMember;
import com.mindspace.model.User;
import com.mindspace.model.UserBlock;
import com.mindspace.repository.ChatMessageRepository;
import com.mindspace.repository.ConversationMemberRepository;
import com.mindspace.repository.ConversationRepository;
import com.mindspace.repository.UserBlockRepository;
import com.mindspace.repository.UserRepository;
import com.mindspace.ws.ChatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Member-to-member direct and group messaging. Users are found by username; contact
 * details are never exposed. Safety controls (block, mute, report) are built in.
 */
@Service
@Transactional
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final ConversationRepository conversationRepo;
    private final ConversationMemberRepository memberRepo;
    private final ChatMessageRepository messageRepo;
    private final UserBlockRepository blockRepo;
    private final UserRepository userRepository;
    private final WebPushService webPushService;
    private final MailService mailService;
    private final ChatWebSocketHandler wsHandler;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Value("${app.contact.recipient:kelvinkiumbe589@gmail.com}")
    private String adminRecipient;

    public ChatService(ConversationRepository conversationRepo,
                       ConversationMemberRepository memberRepo,
                       ChatMessageRepository messageRepo,
                       UserBlockRepository blockRepo,
                       UserRepository userRepository,
                       WebPushService webPushService,
                       MailService mailService,
                       ChatWebSocketHandler wsHandler) {
        this.conversationRepo = conversationRepo;
        this.memberRepo = memberRepo;
        this.messageRepo = messageRepo;
        this.blockRepo = blockRepo;
        this.userRepository = userRepository;
        this.webPushService = webPushService;
        this.mailService = mailService;
        this.wsHandler = wsHandler;
    }

    // ── Find members by username ──────────────────────────────────
    @Transactional(readOnly = true)
    public List<ChatDto.UserResult> searchUsers(String email, String q) {
        User me = getUser(email);
        if (q == null || q.trim().length() < 2) return List.of();
        String needle = q.trim().toLowerCase();
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.USER)
                .filter(u -> !u.getId().equals(me.getId()))
                .filter(u -> u.getUsername() != null && u.getUsername().toLowerCase().contains(needle))
                .filter(u -> !isBlockedEitherWay(me, u))
                .sorted(Comparator.comparing(u -> u.getUsername().toLowerCase()))
                .limit(15)
                .map(u -> new ChatDto.UserResult(u.getId(), u.getUsername()))
                .toList();
    }

    // ── List my conversations (most recent first) ─────────────────
    @Transactional(readOnly = true)
    public List<ChatDto.ConversationSummary> listConversations(String email) {
        User me = getUser(email);
        List<ChatDto.ConversationSummary> out = new ArrayList<>();
        for (ConversationMember cm : memberRepo.findByUser(me)) {
            Conversation c = cm.getConversation();
            List<ConversationMember> members = memberRepo.findByConversation(c);

            ChatDto.ConversationSummary s = new ChatDto.ConversationSummary();
            s.setId(c.getId());
            s.setType(c.getType().name());
            s.setMemberCount(members.size());
            s.setMuted(cm.isMuted());

            if (c.getType() == Conversation.Type.DIRECT) {
                User other = members.stream().map(ConversationMember::getUser)
                        .filter(u -> !u.getId().equals(me.getId())).findFirst().orElse(null);
                String name = other != null ? other.getUsername() : "Unknown";
                s.setTitle(name);
                s.setAvatar(initial(name));
                s.setOtherUserId(other != null ? other.getId() : null);
            } else {
                s.setTitle(c.getName());
                s.setAvatar(initial(c.getName()));
            }

            ChatMessage last = messageRepo.findTopByConversationOrderByCreatedAtDesc(c);
            s.setLastMessage(last == null ? null : (last.isDeleted() ? "Message deleted" : last.getContent()));
            s.setLastMessageAt(c.getLastMessageAt());
            LocalDateTime since = cm.getLastReadAt() != null ? cm.getLastReadAt() : EPOCH;
            s.setUnread(messageRepo.countByConversationAndCreatedAtAfterAndSenderIdNot(c, since, me.getId()));
            out.add(s);
        }
        out.sort(Comparator.comparing(
                (ChatDto.ConversationSummary s) -> s.getLastMessageAt() != null ? s.getLastMessageAt() : EPOCH)
                .reversed());
        return out;
    }

    // ── Open (or create) a 1:1 chat with another member ───────────
    public ChatDto.ConversationDetail openDirect(String email, UUID otherId) {
        User me = getUser(email);
        User other = userRepository.findById(otherId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (other.getId().equals(me.getId())) throw new IllegalArgumentException("You can't message yourself.");
        if (isBlockedEitherWay(me, other)) throw new IllegalArgumentException("You can't message this user.");

        // Reuse an existing direct conversation if one already exists.
        for (ConversationMember cm : memberRepo.findByUser(me)) {
            Conversation c = cm.getConversation();
            if (c.getType() == Conversation.Type.DIRECT
                    && memberRepo.existsByConversationAndUser(c, other)) {
                return getConversation(email, c.getId());
            }
        }

        Conversation c = new Conversation();
        c.setType(Conversation.Type.DIRECT);
        c.setCreatedBy(me);
        c = conversationRepo.save(c);
        memberRepo.save(new ConversationMember(c, me, ConversationMember.Role.MEMBER));
        memberRepo.save(new ConversationMember(c, other, ConversationMember.Role.MEMBER));
        return getConversation(email, c.getId());
    }

    // ── Create a group and add members ────────────────────────────
    public ChatDto.ConversationDetail createGroup(String email, ChatDto.GroupRequest req) {
        User me = getUser(email);
        Conversation c = new Conversation();
        c.setType(Conversation.Type.GROUP);
        c.setName(req.getName().trim());
        c.setCreatedBy(me);
        c = conversationRepo.save(c);
        memberRepo.save(new ConversationMember(c, me, ConversationMember.Role.OWNER));

        for (UUID id : dedupe(req.getMemberIds())) {
            if (id.equals(me.getId())) continue;
            User u = userRepository.findById(id).orElse(null);
            if (u == null || u.getRole() != User.Role.USER) continue;
            if (isBlockedEitherWay(me, u)) continue;
            memberRepo.save(new ConversationMember(c, u, ConversationMember.Role.MEMBER));
            notifyAdded(u, me, c);
        }
        return getConversation(email, c.getId());
    }

    // ── Read a conversation (marks it read) ───────────────────────
    public ChatDto.ConversationDetail getConversation(String email, UUID convId) {
        User me = getUser(email);
        Conversation c = conversationRepo.findById(convId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        ConversationMember cm = memberRepo.findByConversationAndUser(c, me)
                .orElseThrow(() -> new IllegalArgumentException("You're not part of this conversation."));

        List<ConversationMember> members = memberRepo.findByConversation(c);

        ChatDto.ConversationDetail d = new ChatDto.ConversationDetail();
        d.setId(c.getId());
        d.setType(c.getType().name());
        d.setOwner(cm.getRole() == ConversationMember.Role.OWNER);
        if (c.getType() == Conversation.Type.DIRECT) {
            User other = members.stream().map(ConversationMember::getUser)
                    .filter(u -> !u.getId().equals(me.getId())).findFirst().orElse(null);
            d.setTitle(other != null ? other.getUsername() : "Unknown");
        } else {
            d.setTitle(c.getName());
        }
        d.setMembers(members.stream()
                .map(m -> new ChatDto.MemberInfo(m.getUser().getId(), m.getUser().getUsername(), m.getRole().name()))
                .toList());
        d.setMessages(messageRepo.findByConversationOrderByCreatedAtAsc(c).stream()
                .map(m -> toMessageInfo(m, me)).toList());

        // Mark read up to now.
        cm.setLastReadAt(LocalDateTime.now());
        memberRepo.save(cm);
        return d;
    }

    // ── Send a message ────────────────────────────────────────────
    public ChatDto.MessageInfo sendMessage(String email, UUID convId, String content) {
        User me = getUser(email);
        Conversation c = conversationRepo.findById(convId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        ConversationMember mine = memberRepo.findByConversationAndUser(c, me)
                .orElseThrow(() -> new IllegalArgumentException("You're not part of this conversation."));
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Message cannot be empty.");

        List<ConversationMember> members = memberRepo.findByConversation(c);
        if (c.getType() == Conversation.Type.DIRECT) {
            User other = members.stream().map(ConversationMember::getUser)
                    .filter(u -> !u.getId().equals(me.getId())).findFirst().orElse(null);
            if (other != null && isBlockedEitherWay(me, other)) {
                throw new IllegalArgumentException("You can no longer message this user.");
            }
        }

        ChatMessage m = messageRepo.save(new ChatMessage(c, me, text));
        c.setLastMessageAt(m.getCreatedAt() != null ? m.getCreatedAt() : LocalDateTime.now());
        conversationRepo.save(c);
        mine.setLastReadAt(LocalDateTime.now());
        memberRepo.save(mine);

        deliver(c, m, me, members);
        return toMessageInfo(m, me);
    }

    // ── Add members to a group ────────────────────────────────────
    public ChatDto.ConversationDetail addMembers(String email, UUID convId, List<UUID> ids) {
        User me = getUser(email);
        Conversation c = conversationRepo.findById(convId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        if (c.getType() != Conversation.Type.GROUP) throw new IllegalArgumentException("You can only add people to a group.");
        memberRepo.findByConversationAndUser(c, me)
                .orElseThrow(() -> new IllegalArgumentException("You're not part of this group."));

        for (UUID id : dedupe(ids)) {
            User u = userRepository.findById(id).orElse(null);
            if (u == null || u.getRole() != User.Role.USER) continue;
            if (memberRepo.existsByConversationAndUser(c, u)) continue;
            if (isBlockedEitherWay(me, u)) continue;
            memberRepo.save(new ConversationMember(c, u, ConversationMember.Role.MEMBER));
            notifyAdded(u, me, c);
        }
        return getConversation(email, convId);
    }

    // ── Leave a conversation ──────────────────────────────────────
    public void leave(String email, UUID convId) {
        User me = getUser(email);
        Conversation c = conversationRepo.findById(convId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        ConversationMember mine = memberRepo.findByConversationAndUser(c, me)
                .orElseThrow(() -> new IllegalArgumentException("You're not part of this conversation."));
        boolean wasOwner = mine.getRole() == ConversationMember.Role.OWNER;
        memberRepo.delete(mine);

        List<ConversationMember> remaining = memberRepo.findByConversation(c);
        if (remaining.isEmpty()) {
            messageRepo.deleteByConversation(c);
            conversationRepo.delete(c);
        } else if (wasOwner && c.getType() == Conversation.Type.GROUP) {
            // Hand ownership to the oldest remaining member.
            ConversationMember next = remaining.stream()
                    .min(Comparator.comparing(m -> m.getJoinedAt() != null ? m.getJoinedAt() : EPOCH))
                    .orElse(remaining.get(0));
            next.setRole(ConversationMember.Role.OWNER);
            memberRepo.save(next);
        }
    }

    // Owner removes another member from a group.
    public ChatDto.ConversationDetail removeMember(String email, UUID convId, UUID userId) {
        User me = getUser(email);
        Conversation c = conversationRepo.findById(convId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        ConversationMember mine = memberRepo.findByConversationAndUser(c, me)
                .orElseThrow(() -> new IllegalArgumentException("You're not part of this group."));
        if (mine.getRole() != ConversationMember.Role.OWNER) {
            throw new IllegalArgumentException("Only the group owner can remove members.");
        }
        if (userId.equals(me.getId())) throw new IllegalArgumentException("Use 'Leave group' to remove yourself.");
        User target = userRepository.findById(userId).orElse(null);
        if (target != null) {
            memberRepo.findByConversationAndUser(c, target).ifPresent(memberRepo::delete);
        }
        return getConversation(email, convId);
    }

    // ── Mute / unmute a conversation ──────────────────────────────
    public void setMuted(String email, UUID convId, boolean muted) {
        User me = getUser(email);
        Conversation c = conversationRepo.findById(convId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        ConversationMember mine = memberRepo.findByConversationAndUser(c, me)
                .orElseThrow(() -> new IllegalArgumentException("You're not part of this conversation."));
        mine.setMuted(muted);
        memberRepo.save(mine);
    }

    // ── Block / unblock ───────────────────────────────────────────
    public void block(String email, UUID targetId) {
        User me = getUser(email);
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (target.getId().equals(me.getId())) throw new IllegalArgumentException("You can't block yourself.");
        if (!blockRepo.existsByBlockerAndBlocked(me, target)) {
            blockRepo.save(new UserBlock(me, target));
        }
    }

    public void unblock(String email, UUID targetId) {
        User me = getUser(email);
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        blockRepo.findByBlockerAndBlocked(me, target).ifPresent(blockRepo::delete);
    }

    @Transactional(readOnly = true)
    public List<ChatDto.UserResult> listBlocked(String email) {
        User me = getUser(email);
        return blockRepo.findByBlocker(me).stream()
                .map(b -> new ChatDto.UserResult(b.getBlocked().getId(), b.getBlocked().getUsername()))
                .toList();
    }

    // ── Report a user to the admin ────────────────────────────────
    public void report(String email, ChatDto.ReportRequest req) {
        User me = getUser(email);
        User target = req.getUserId() != null ? userRepository.findById(req.getUserId()).orElse(null) : null;
        String targetName = target != null ? target.getUsername() : "(unknown)";
        String reason = req.getReason() != null ? req.getReason() : "(no reason given)";
        String subject = "MindSpace report: " + me.getUsername() + " reported " + targetName;
        String body = "Reporter: " + me.getUsername() + " <" + me.getEmail() + ">\n"
                + "Reported user: " + targetName + (target != null ? " <" + target.getEmail() + ">" : "") + "\n"
                + (req.getConversationId() != null ? "Conversation: " + req.getConversationId() + "\n" : "")
                + "\nReason:\n" + reason
                + "\n\nReview in the admin dashboard and take action (warn/suspend) if warranted.";
        Thread t = new Thread(() -> {
            try { mailService.send(adminRecipient, subject, body); }
            catch (Exception e) { log.warn("report email failed: {}", e.getMessage()); }
        }, "chat-report");
        t.setDaemon(true);
        t.start();
    }

    // ── Helpers ───────────────────────────────────────────────────
    private void deliver(Conversation c, ChatMessage m, User sender, List<ConversationMember> members) {
        for (ConversationMember cm : members) {
            User u = cm.getUser();
            // Live update to every member's open clients (including the sender's other tabs).
            wsSend(u.getId(), c.getId(), m, u);
            if (u.getId().equals(sender.getId())) continue;
            // Offline + not muted → web push so they don't miss it.
            if (!cm.isMuted() && !wsHandler.isOnline(u.getId())) {
                String title = c.getType() == Conversation.Type.GROUP ? c.getName() : sender.getUsername();
                String preview = m.getContent().length() > 80 ? m.getContent().substring(0, 80) + "…" : m.getContent();
                String bodyText = c.getType() == Conversation.Type.GROUP ? sender.getUsername() + ": " + preview : preview;
                try { webPushService.sendToUser(u, title, bodyText, "/messages"); } catch (Exception ignored) {}
            }
        }
    }

    private void wsSend(UUID userId, UUID convId, ChatMessage m, User viewer) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "message");
            payload.put("conversationId", convId.toString());
            payload.put("message", toMessageInfo(m, viewer));
            wsHandler.sendToUser(userId, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.debug("chat ws send failed: {}", e.getMessage());
        }
    }

    private void notifyAdded(User added, User by, Conversation c) {
        if (wsHandler.isOnline(added.getId())) return;
        try {
            webPushService.sendToUser(added, "Added to a group",
                    by.getUsername() + " added you to \"" + c.getName() + "\"", "/messages");
        } catch (Exception ignored) {}
    }

    private ChatDto.MessageInfo toMessageInfo(ChatMessage m, User viewer) {
        boolean mine = viewer != null && m.getSender() != null && m.getSender().getId().equals(viewer.getId());
        String senderName = m.getSender() != null ? m.getSender().getUsername() : "Unknown";
        String content = m.isDeleted() ? "" : m.getContent();
        return new ChatDto.MessageInfo(m.getId(),
                m.getSender() != null ? m.getSender().getId() : null,
                senderName, content, mine, m.isDeleted(), m.getCreatedAt());
    }

    private boolean isBlockedEitherWay(User a, User b) {
        return blockRepo.existsByBlockerAndBlocked(a, b) || blockRepo.existsByBlockerAndBlocked(b, a);
    }

    private List<UUID> dedupe(List<UUID> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private String initial(String name) {
        if (name == null || name.isBlank()) return "?";
        return name.trim().substring(0, 1).toUpperCase();
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
