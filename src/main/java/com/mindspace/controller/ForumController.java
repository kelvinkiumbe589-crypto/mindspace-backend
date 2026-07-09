package com.mindspace.controller;

import com.mindspace.dto.ForumDto;
import com.mindspace.service.ForumService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/forum")
public class ForumController {

    private final ForumService forumService;

    public ForumController(ForumService forumService) {
        this.forumService = forumService;
    }

    // GET /api/forum/posts
    // Public, but if a valid token is sent we resolve likedByMe for the reader.
    @GetMapping("/posts")
    public ResponseEntity<List<ForumDto.PostResponse>> getAllPosts(
            @AuthenticationPrincipal UserDetails userDetails) {
        String email = userDetails != null ? userDetails.getUsername() : null;
        return ResponseEntity.ok(forumService.getAllPosts(email));
    }

    // POST /api/forum/posts
    @PostMapping("/posts")
    public ResponseEntity<ForumDto.PostResponse> createPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ForumDto.CreatePostRequest request) {
        return ResponseEntity.ok(forumService.createPost(userDetails.getUsername(), request));
    }

    // GET /api/forum/posts/{id}
    @GetMapping("/posts/{id}")
    public ResponseEntity<ForumDto.PostDetailResponse> getPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        String email = userDetails != null ? userDetails.getUsername() : null;
        return ResponseEntity.ok(forumService.getPostById(id, email));
    }

    // POST /api/forum/posts/{id}/view — record an impression (public, no auth).
    @PostMapping("/posts/{id}/view")
    public ResponseEntity<java.util.Map<String, Object>> recordView(@PathVariable UUID id) {
        int views = forumService.recordView(id);
        return ResponseEntity.ok(java.util.Map.of("viewCount", views));
    }

    // POST /api/forum/posts/{id}/like — toggle the signed-in user's like.
    @PostMapping("/posts/{id}/like")
    public ResponseEntity<ForumDto.LikeResponse> toggleLike(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return ResponseEntity.ok(forumService.toggleLike(userDetails.getUsername(), id));
    }

    // POST /api/forum/posts/{id}/reply
    @PostMapping("/posts/{id}/reply")
    public ResponseEntity<ForumDto.ReplyResponse> replyToPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody ForumDto.CreateReplyRequest request) {
        return ResponseEntity.ok(forumService.replyToPost(userDetails.getUsername(), id, request));
    }

    // PUT /api/forum/posts/{id} — edit your own post
    @PutMapping("/posts/{id}")
    public ResponseEntity<ForumDto.PostResponse> editPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody ForumDto.CreatePostRequest request) {
        return ResponseEntity.ok(forumService.editPost(userDetails.getUsername(), id, request));
    }

    // DELETE /api/forum/posts/{id} — delete your own post
    @DeleteMapping("/posts/{id}")
    public ResponseEntity<java.util.Map<String, Object>> deletePost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        forumService.deletePost(userDetails.getUsername(), id);
        return ResponseEntity.ok(java.util.Map.of("deleted", true));
    }

    // PUT /api/forum/replies/{id} — edit your own comment
    @PutMapping("/replies/{id}")
    public ResponseEntity<ForumDto.ReplyResponse> editReply(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody ForumDto.CreateReplyRequest request) {
        return ResponseEntity.ok(forumService.editReply(userDetails.getUsername(), id, request.getContent()));
    }

    // DELETE /api/forum/replies/{id} — delete your own comment
    @DeleteMapping("/replies/{id}")
    public ResponseEntity<java.util.Map<String, Object>> deleteReply(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        forumService.deleteReply(userDetails.getUsername(), id);
        return ResponseEntity.ok(java.util.Map.of("deleted", true));
    }
}
