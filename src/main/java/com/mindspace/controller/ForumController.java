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
    @GetMapping("/posts")
    public ResponseEntity<List<ForumDto.PostResponse>> getAllPosts() {
        return ResponseEntity.ok(forumService.getAllPosts());
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
    public ResponseEntity<ForumDto.PostDetailResponse> getPost(@PathVariable UUID id) {
        return ResponseEntity.ok(forumService.getPostById(id));
    }

    // POST /api/forum/posts/{id}/reply
    @PostMapping("/posts/{id}/reply")
    public ResponseEntity<ForumDto.ReplyResponse> replyToPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody ForumDto.CreateReplyRequest request) {
        return ResponseEntity.ok(forumService.replyToPost(userDetails.getUsername(), id, request));
    }
}
