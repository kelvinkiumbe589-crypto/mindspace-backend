package com.mindspace.service;

import com.mindspace.dto.ForumDto;
import com.mindspace.model.ForumPost;
import com.mindspace.model.ForumReply;
import com.mindspace.model.User;
import com.mindspace.repository.ForumPostRepository;
import com.mindspace.repository.ForumReplyRepository;
import com.mindspace.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ForumService {

    private final ForumPostRepository forumPostRepository;
    private final ForumReplyRepository forumReplyRepository;
    private final UserRepository userRepository;

    public ForumService(ForumPostRepository forumPostRepository,
                        ForumReplyRepository forumReplyRepository,
                        UserRepository userRepository) {
        this.forumPostRepository = forumPostRepository;
        this.forumReplyRepository = forumReplyRepository;
        this.userRepository = userRepository;
    }

    // ── Create a post ─────────────────────────────────────────────
    public ForumDto.PostResponse createPost(String email, ForumDto.CreatePostRequest request) {
        User user = getUser(email);

        ForumPost post = new ForumPost();
        post.setUser(user);
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setIsAnonymous(request.getIsAnonymous());
        post.setCategory(request.getCategory());

        ForumPost saved = forumPostRepository.save(post);
        return toPostResponse(saved);
    }

    // ── Get all posts ─────────────────────────────────────────────
    public List<ForumDto.PostResponse> getAllPosts() {
        return forumPostRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toPostResponse)
                .toList();
    }

    // ── Get single post with replies ──────────────────────────────
    public ForumDto.PostDetailResponse getPostById(UUID postId) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        List<ForumReply> replies = forumReplyRepository.findByPostOrderByCreatedAtAsc(post);

        ForumDto.PostDetailResponse response = new ForumDto.PostDetailResponse();
        response.setId(post.getId());
        response.setTitle(post.getTitle());
        response.setContent(post.getContent());
        response.setAuthor(resolveAuthor(post.getUser(), post.getIsAnonymous()));
        response.setCategory(post.getCategory());
        response.setCreatedAt(post.getCreatedAt());
        response.setReplies(replies.stream().map(this::toReplyResponse).toList());
        return response;
    }

    // ── Reply to a post ───────────────────────────────────────────
    public ForumDto.ReplyResponse replyToPost(String email, UUID postId,
                                               ForumDto.CreateReplyRequest request) {
        User user = getUser(email);
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        ForumReply reply = new ForumReply();
        reply.setPost(post);
        reply.setUser(user);
        reply.setContent(request.getContent());
        reply.setIsAnonymous(request.getIsAnonymous());

        return toReplyResponse(forumReplyRepository.save(reply));
    }

    // ── Helpers ───────────────────────────────────────────────────
    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private String resolveAuthor(User user, Boolean isAnonymous) {
        if (isAnonymous == null || isAnonymous || user == null) return "Anonymous";
        return user.getUsername();
    }

    private ForumDto.PostResponse toPostResponse(ForumPost post) {
        ForumDto.PostResponse response = new ForumDto.PostResponse();
        response.setId(post.getId());
        response.setTitle(post.getTitle());
        response.setContent(post.getContent());
        response.setAuthor(resolveAuthor(post.getUser(), post.getIsAnonymous()));
        response.setCategory(post.getCategory());
        response.setReplyCount(post.getReplies() != null ? post.getReplies().size() : 0);
        response.setCreatedAt(post.getCreatedAt());
        return response;
    }

    private ForumDto.ReplyResponse toReplyResponse(ForumReply reply) {
        ForumDto.ReplyResponse response = new ForumDto.ReplyResponse();
        response.setId(reply.getId());
        response.setContent(reply.getContent());
        response.setAuthor(resolveAuthor(reply.getUser(), reply.getIsAnonymous()));
        response.setCreatedAt(reply.getCreatedAt());
        return response;
    }
}
