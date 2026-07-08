package com.mindspace.controller;

import com.mindspace.dto.ChatDto;
import com.mindspace.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    private String email(UserDetails u) { return u.getUsername(); }

    // GET /api/chat/users/search?q=
    @GetMapping("/users/search")
    public ResponseEntity<List<ChatDto.UserResult>> search(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam("q") String q) {
        return ResponseEntity.ok(chatService.searchUsers(email(user), q));
    }

    // GET /api/chat/conversations
    @GetMapping("/conversations")
    public ResponseEntity<List<ChatDto.ConversationSummary>> conversations(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(chatService.listConversations(email(user)));
    }

    // POST /api/chat/conversations/direct  {userId}
    @PostMapping("/conversations/direct")
    public ResponseEntity<ChatDto.ConversationDetail> openDirect(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody ChatDto.DirectRequest req) {
        return ResponseEntity.ok(chatService.openDirect(email(user), req.getUserId()));
    }

    // POST /api/chat/conversations/group  {name, memberIds}
    @PostMapping("/conversations/group")
    public ResponseEntity<ChatDto.ConversationDetail> createGroup(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody ChatDto.GroupRequest req) {
        return ResponseEntity.ok(chatService.createGroup(email(user), req));
    }

    // GET /api/chat/conversations/{id}
    @GetMapping("/conversations/{id}")
    public ResponseEntity<ChatDto.ConversationDetail> get(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(chatService.getConversation(email(user), id));
    }

    // POST /api/chat/conversations/{id}/messages  {content}
    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<ChatDto.MessageInfo> send(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody ChatDto.SendRequest req) {
        return ResponseEntity.ok(chatService.sendMessage(email(user), id, req.getContent()));
    }

    // POST /api/chat/conversations/{id}/members  {memberIds}
    @PostMapping("/conversations/{id}/members")
    public ResponseEntity<ChatDto.ConversationDetail> addMembers(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody ChatDto.AddMembersRequest req) {
        return ResponseEntity.ok(chatService.addMembers(email(user), id, req.getMemberIds()));
    }

    // DELETE /api/chat/conversations/{id}/members/{userId}  (owner removes a member)
    @DeleteMapping("/conversations/{id}/members/{userId}")
    public ResponseEntity<ChatDto.ConversationDetail> removeMember(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(chatService.removeMember(email(user), id, userId));
    }

    // POST /api/chat/conversations/{id}/leave
    @PostMapping("/conversations/{id}/leave")
    public ResponseEntity<Map<String, Object>> leave(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable UUID id) {
        chatService.leave(email(user), id);
        return ResponseEntity.ok(Map.of("left", true));
    }

    // POST /api/chat/conversations/{id}/mute  {muted}
    @PostMapping("/conversations/{id}/mute")
    public ResponseEntity<Map<String, Object>> mute(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable UUID id,
            @RequestBody ChatDto.MuteRequest req) {
        chatService.setMuted(email(user), id, req.isMuted());
        return ResponseEntity.ok(Map.of("muted", req.isMuted()));
    }

    // POST /api/chat/block  {userId}
    @PostMapping("/block")
    public ResponseEntity<Map<String, Object>> block(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody ChatDto.UserRequest req) {
        chatService.block(email(user), req.getUserId());
        return ResponseEntity.ok(Map.of("blocked", true));
    }

    // POST /api/chat/unblock  {userId}
    @PostMapping("/unblock")
    public ResponseEntity<Map<String, Object>> unblock(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody ChatDto.UserRequest req) {
        chatService.unblock(email(user), req.getUserId());
        return ResponseEntity.ok(Map.of("unblocked", true));
    }

    // GET /api/chat/blocked
    @GetMapping("/blocked")
    public ResponseEntity<List<ChatDto.UserResult>> blocked(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(chatService.listBlocked(email(user)));
    }

    // POST /api/chat/report  {userId, conversationId?, reason}
    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> report(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody ChatDto.ReportRequest req) {
        chatService.report(email(user), req);
        return ResponseEntity.ok(Map.of("reported", true));
    }
}
