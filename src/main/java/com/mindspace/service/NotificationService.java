package com.mindspace.service;

import com.mindspace.dto.NotificationDto;
import com.mindspace.model.Notification;
import com.mindspace.model.User;
import com.mindspace.repository.NotificationRepository;
import com.mindspace.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository repo;
    private final UserRepository userRepository;
    private final WebPushService webPushService;

    public NotificationService(NotificationRepository repo, UserRepository userRepository,
                               WebPushService webPushService) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.webPushService = webPushService;
    }

    /** Persist an in-app notification for a recipient, and push it to their devices. */
    public void create(User recipient, String type, String message, String link) {
        if (recipient == null) return;
        repo.save(new Notification(recipient, type, message, link));
        webPushService.sendToUser(recipient, "MindSpace", message, link);
    }

    public List<NotificationDto.Item> list(String email) {
        User user = getUser(email);
        return repo.findTop50ByUserOrderByCreatedAtDesc(user).stream()
                .map(n -> new NotificationDto.Item(
                        n.getId(), n.getType(), n.getMessage(), n.getLink(), n.isRead(), n.getCreatedAt()))
                .toList();
    }

    public long unreadCount(String email) {
        return repo.countByUserAndReadFalse(getUser(email));
    }

    public void markAllRead(String email) {
        User user = getUser(email);
        List<Notification> unread = repo.findByUserAndReadFalse(user);
        unread.forEach(n -> n.setRead(true));
        repo.saveAll(unread);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
