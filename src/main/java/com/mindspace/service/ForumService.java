package com.mindspace.service;

import com.mindspace.dto.ForumDto;
import com.mindspace.model.ForumPost;
import com.mindspace.model.ForumPostLike;
import com.mindspace.model.ForumReply;
import com.mindspace.model.User;
import com.mindspace.repository.ForumPostLikeRepository;
import com.mindspace.repository.ForumPostRepository;
import com.mindspace.repository.ForumReplyRepository;
import com.mindspace.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ForumService {

    private final ForumPostRepository forumPostRepository;
    private final ForumReplyRepository forumReplyRepository;
    private final ForumPostLikeRepository forumPostLikeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final WebPushService webPushService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public ForumService(ForumPostRepository forumPostRepository,
                        ForumReplyRepository forumReplyRepository,
                        ForumPostLikeRepository forumPostLikeRepository,
                        UserRepository userRepository,
                        NotificationService notificationService,
                        MailService mailService,
                        WebPushService webPushService) {
        this.forumPostRepository = forumPostRepository;
        this.forumReplyRepository = forumReplyRepository;
        this.forumPostLikeRepository = forumPostLikeRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.mailService = mailService;
        this.webPushService = webPushService;
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
        return toPostResponse(saved, user);
    }

    // ── Get all posts ─────────────────────────────────────────────
    // currentEmail is nullable — anonymous readers just get likedByMe = false.
    public List<ForumDto.PostResponse> getAllPosts(String currentEmail) {
        User currentUser = userOrNull(currentEmail);
        return forumPostRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(post -> toPostResponse(post, currentUser))
                .toList();
    }

    // ── Get single post with replies ──────────────────────────────
    public ForumDto.PostDetailResponse getPostById(UUID postId, String currentEmail) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        User currentUser = userOrNull(currentEmail);
        List<ForumReply> replies = forumReplyRepository.findByPostOrderByCreatedAtAsc(post);

        ForumDto.PostDetailResponse response = new ForumDto.PostDetailResponse();
        response.setId(post.getId());
        response.setTitle(post.getTitle());
        response.setContent(post.getContent());
        response.setAuthor(resolveAuthor(post.getUser(), post.getIsAnonymous()));
        response.setCategory(post.getCategory());
        response.setLikeCount((int) forumPostLikeRepository.countByPost(post));
        response.setLikedByMe(currentUser != null && forumPostLikeRepository.existsByPostAndUser(post, currentUser));
        response.setCreatedAt(post.getCreatedAt());
        response.setReplies(replies.stream().map(r -> toReplyResponse(r, currentUser)).toList());
        return response;
    }

    // ── Toggle a like on a post ───────────────────────────────────
    // Likes are live and shared: liking twice removes the like (a toggle).
    @Transactional
    public ForumDto.LikeResponse toggleLike(String email, UUID postId) {
        User user = getUser(email);
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        boolean liked;
        var existing = forumPostLikeRepository.findByPostAndUser(post, user);
        if (existing.isPresent()) {
            forumPostLikeRepository.delete(existing.get());
            liked = false;
        } else {
            ForumPostLike like = new ForumPostLike();
            like.setPost(post);
            like.setUser(user);
            forumPostLikeRepository.save(like);
            liked = true;
        }
        int count = (int) forumPostLikeRepository.countByPost(post);
        return new ForumDto.LikeResponse(count, liked);
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
        ForumReply saved = forumReplyRepository.save(reply);

        notifyAuthorOfReply(post, user, request);
        return toReplyResponse(saved, user);
    }

    // ── Edit / delete your own comment ────────────────────────────
    @Transactional
    public ForumDto.ReplyResponse editReply(String email, UUID replyId, String content) {
        User user = getUser(email);
        ForumReply reply = forumReplyRepository.findById(replyId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));
        requireOwner(reply, user);
        reply.setContent(content);
        return toReplyResponse(forumReplyRepository.save(reply), user);
    }

    @Transactional
    public void deleteReply(String email, UUID replyId) {
        User user = getUser(email);
        ForumReply reply = forumReplyRepository.findById(replyId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));
        requireOwner(reply, user);
        forumReplyRepository.delete(reply);
    }

    private void requireOwner(ForumReply reply, User user) {
        if (reply.getUser() == null || !reply.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You can only edit or delete your own comment.");
        }
    }

    // Alert the post's author (in-app + email) that someone replied — unless they're
    // replying to their own post. The replier's name respects their anonymous choice.
    private void notifyAuthorOfReply(ForumPost post, User replier, ForumDto.CreateReplyRequest request) {
        User author = post.getUser();
        if (author == null) return;
        if (replier != null && author.getId().equals(replier.getId())) return;

        boolean anon = Boolean.TRUE.equals(request.getIsAnonymous());
        String who = (anon || replier == null) ? "Someone" : replier.getUsername();
        String message = who + " replied to your post \"" + post.getTitle() + "\"";
        // In-app bell + push (if the author has enabled it).
        notificationService.create(author, "FORUM_REPLY", message, "/community-forum");
        // Email only as a fallback — if they get push, don't also spam their inbox.
        if (!webPushService.hasSubscription(author)) {
            mailService.sendForumReplyAsync(author.getEmail(), author.getUsername(), who,
                    post.getTitle(), request.getContent(), frontendUrl + "/community-forum");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────
    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    // Resolve the signed-in user, or null for anonymous/unauthenticated readers.
    private User userOrNull(String email) {
        if (email == null) return null;
        return userRepository.findByEmail(email).orElse(null);
    }

    private String resolveAuthor(User user, Boolean isAnonymous) {
        if (isAnonymous == null || isAnonymous || user == null) return "Anonymous";
        return user.getUsername();
    }

    private ForumDto.PostResponse toPostResponse(ForumPost post, User currentUser) {
        ForumDto.PostResponse response = new ForumDto.PostResponse();
        response.setId(post.getId());
        response.setTitle(post.getTitle());
        response.setContent(post.getContent());
        response.setAuthor(resolveAuthor(post.getUser(), post.getIsAnonymous()));
        response.setCategory(post.getCategory());
        response.setReplyCount(post.getReplies() != null ? post.getReplies().size() : 0);
        response.setLikeCount((int) forumPostLikeRepository.countByPost(post));
        response.setLikedByMe(currentUser != null && forumPostLikeRepository.existsByPostAndUser(post, currentUser));
        response.setCreatedAt(post.getCreatedAt());
        return response;
    }

    private ForumDto.ReplyResponse toReplyResponse(ForumReply reply, User currentUser) {
        ForumDto.ReplyResponse response = new ForumDto.ReplyResponse();
        response.setId(reply.getId());
        response.setContent(reply.getContent());
        response.setAuthor(resolveAuthor(reply.getUser(), reply.getIsAnonymous()));
        response.setMine(currentUser != null && reply.getUser() != null
                && reply.getUser().getId().equals(currentUser.getId()));
        response.setCreatedAt(reply.getCreatedAt());
        return response;
    }
}
