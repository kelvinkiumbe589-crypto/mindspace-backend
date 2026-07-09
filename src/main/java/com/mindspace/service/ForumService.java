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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ForumService {

    private static final Logger log = LoggerFactory.getLogger(ForumService.class);

    private final ForumPostRepository forumPostRepository;
    private final ForumReplyRepository forumReplyRepository;
    private final ForumPostLikeRepository forumPostLikeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final WebPushService webPushService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.contact.recipient:kelvinkiumbe589@gmail.com}")
    private String adminRecipient;

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
        applyMedia(post, request.getMediaUrl(), request.getMediaType());

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
        response.setMediaUrl(post.getMediaUrl());
        response.setMediaType(post.getMediaType());
        response.setViewCount(post.getViewCount());
        response.setLikeCount((int) forumPostLikeRepository.countByPost(post));
        response.setLikedByMe(currentUser != null && forumPostLikeRepository.existsByPostAndUser(post, currentUser));
        response.setCreatedAt(post.getCreatedAt());
        response.setReplies(replies.stream().map(r -> toReplyResponse(r, currentUser)).toList());
        return response;
    }

    // ── Report a post or comment to the admin ─────────────────────
    public void report(String email, ForumDto.ReportRequest req) {
        User me = getUser(email);
        String reason = (req.getReason() != null && !req.getReason().isBlank())
                ? req.getReason() : "(no reason given)";

        String what, author, snippet, ref;
        if (req.getReplyId() != null) {
            ForumReply r = forumReplyRepository.findById(req.getReplyId()).orElse(null);
            what = "comment";
            author = r != null ? resolveAuthor(r.getUser(), r.getIsAnonymous()) : "(unknown)";
            snippet = r != null ? r.getContent() : "(already deleted)";
            ref = req.getReplyId().toString();
        } else if (req.getPostId() != null) {
            ForumPost p = forumPostRepository.findById(req.getPostId()).orElse(null);
            what = "post";
            author = p != null ? resolveAuthor(p.getUser(), p.getIsAnonymous()) : "(unknown)";
            snippet = p != null ? (p.getTitle() + " — " + p.getContent()) : "(already deleted)";
            ref = req.getPostId().toString();
        } else {
            throw new IllegalArgumentException("Nothing to report.");
        }
        if (snippet != null && snippet.length() > 500) snippet = snippet.substring(0, 500) + "…";

        String subject = "MindSpace forum report: " + me.getUsername() + " reported a " + what;
        final String body = "Reporter: " + me.getUsername() + " <" + me.getEmail() + ">\n"
                + "Reported " + what + " by: " + author + "\n"
                + what + " id: " + ref + "\n"
                + "\nContent:\n" + snippet
                + "\n\nReason:\n" + reason
                + "\n\nReview in the community forum and take action if warranted.";
        Thread t = new Thread(() -> {
            try { mailService.send(adminRecipient, subject, body); }
            catch (Exception e) { log.warn("forum report email failed: {}", e.getMessage()); }
        }, "forum-report");
        t.setDaemon(true);
        t.start();
    }

    // ── Record a view (impression) ────────────────────────────────
    // Public + best-effort: no auth, de-duplication is handled client-side
    // (once per post per session). Returns the new count, or -1 if not found.
    @Transactional
    public int recordView(UUID postId) {
        return forumPostRepository.incrementViewCount(postId) > 0
                ? forumPostRepository.findById(postId).map(ForumPost::getViewCount).orElse(-1)
                : -1;
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

    // Attach (or clear) a post's single media item. Only image/video are accepted;
    // anything else is dropped so a bad client can't store arbitrary blobs.
    private void applyMedia(ForumPost post, String mediaUrl, String mediaType) {
        boolean hasMedia = mediaUrl != null && !mediaUrl.isBlank()
                && ("image".equals(mediaType) || "video".equals(mediaType));
        post.setMediaUrl(hasMedia ? mediaUrl : null);
        post.setMediaType(hasMedia ? mediaType : null);
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
        response.setMediaUrl(post.getMediaUrl());
        response.setMediaType(post.getMediaType());
        response.setViewCount(post.getViewCount());
        response.setReplyCount(post.getReplies() != null ? post.getReplies().size() : 0);
        response.setLikeCount((int) forumPostLikeRepository.countByPost(post));
        response.setLikedByMe(currentUser != null && forumPostLikeRepository.existsByPostAndUser(post, currentUser));
        response.setMine(currentUser != null && post.getUser() != null
                && post.getUser().getId().equals(currentUser.getId()));
        response.setCreatedAt(post.getCreatedAt());
        return response;
    }

    // ── Edit / delete your own post ───────────────────────────────
    @Transactional
    public ForumDto.PostResponse editPost(String email, UUID postId, ForumDto.CreatePostRequest req) {
        User user = getUser(email);
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        requirePostOwner(post, user);
        post.setTitle(req.getTitle());
        post.setContent(req.getContent());
        if (req.getCategory() != null && !req.getCategory().isBlank()) post.setCategory(req.getCategory());
        applyMedia(post, req.getMediaUrl(), req.getMediaType());
        return toPostResponse(forumPostRepository.save(post), user);
    }

    @Transactional
    public void deletePost(String email, UUID postId) {
        User user = getUser(email);
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        requirePostOwner(post, user);
        forumPostLikeRepository.deleteByPost(post); // likes aren't cascaded
        forumPostRepository.delete(post);           // replies cascade
    }

    private void requirePostOwner(ForumPost post, User user) {
        if (post.getUser() == null || !post.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You can only edit or delete your own post.");
        }
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
