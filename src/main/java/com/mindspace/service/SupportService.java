package com.mindspace.service;

import com.mindspace.dto.SupportDto;
import com.mindspace.model.SupportMessage;
import com.mindspace.model.User;
import com.mindspace.repository.SupportMessageRepository;
import com.mindspace.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class SupportService {

    private final SupportMessageRepository repo;
    private final UserRepository userRepository;

    public SupportService(SupportMessageRepository repo, UserRepository userRepository) {
        this.repo = repo;
        this.userRepository = userRepository;
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    private SupportDto.MessageResponse toResponse(SupportMessage m) {
        return new SupportDto.MessageResponse(m.getId(), m.getText(), m.isFromAdmin(), m.getCreatedAt());
    }

    public SupportDto.MessageResponse sendUserMessage(String email, String text) {
        User user = getUser(email);
        return toResponse(repo.save(new SupportMessage(user, text, false)));
    }

    public List<SupportDto.MessageResponse> myThread(String email) {
        User user = getUser(email);
        return repo.findByUserOrderByCreatedAtAsc(user).stream().map(this::toResponse).toList();
    }

    // ── Admin ──
    public List<SupportDto.Conversation> conversations() {
        List<SupportMessage> all = repo.findAllByOrderByCreatedAtAsc();
        Map<UUID, List<SupportMessage>> byUser = new LinkedHashMap<>();
        for (SupportMessage m : all) {
            byUser.computeIfAbsent(m.getUser().getId(), k -> new ArrayList<>()).add(m);
        }
        List<SupportDto.Conversation> convos = new ArrayList<>();
        for (Map.Entry<UUID, List<SupportMessage>> e : byUser.entrySet()) {
            List<SupportMessage> msgs = e.getValue();
            SupportMessage last = msgs.get(msgs.size() - 1);
            User u = last.getUser();
            convos.add(new SupportDto.Conversation(
                    u.getId(), u.getUsername(), u.getEmail(),
                    last.getText(), last.isFromAdmin(), last.getCreatedAt(), msgs.size()));
        }
        convos.sort((a, b) -> b.getLastAt().compareTo(a.getLastAt()));
        return convos;
    }

    public List<SupportDto.MessageResponse> thread(UUID userId) {
        return repo.findByUserIdOrderByCreatedAtAsc(userId).stream().map(this::toResponse).toList();
    }

    public SupportDto.MessageResponse adminReply(UUID userId, String text) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toResponse(repo.save(new SupportMessage(user, text, true)));
    }
}
