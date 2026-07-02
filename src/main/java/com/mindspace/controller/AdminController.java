package com.mindspace.controller;

import com.mindspace.model.User;
import com.mindspace.repository.MoodEntryRepository;
import com.mindspace.repository.SupportMessageRepository;
import com.mindspace.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final MoodEntryRepository moodEntryRepository;
    private final SupportMessageRepository supportMessageRepository;

    public AdminController(UserRepository userRepository, MoodEntryRepository moodEntryRepository,
                           SupportMessageRepository supportMessageRepository) {
        this.userRepository = userRepository;
        this.moodEntryRepository = moodEntryRepository;
        this.supportMessageRepository = supportMessageRepository;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        LocalDateTime now = LocalDateTime.now();

        long totalUsers = userRepository.count();
        long newUsers7d = userRepository.countByCreatedAtAfter(now.minusDays(7));
        long newUsers30d = userRepository.countByCreatedAtAfter(now.minusDays(30));
        long totalMoods = moodEntryRepository.count();
        long conversations = supportMessageRepository.findAll().stream()
                .map(m -> m.getUser().getId()).distinct().count();

        // Signups per day for the last 7 days
        List<User> users = userRepository.findAll();
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) counts.put(LocalDate.now().minusDays(i), 0);
        for (User u : users) {
            if (u.getCreatedAt() == null) continue;
            LocalDate d = u.getCreatedAt().toLocalDate();
            if (counts.containsKey(d)) counts.merge(d, 1, Integer::sum);
        }
        List<Map<String, Object>> signupsByDay = new ArrayList<>();
        counts.forEach((day, count) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day.toString());
            m.put("count", count);
            signupsByDay.add(m);
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalUsers", totalUsers);
        out.put("newUsers7d", newUsers7d);
        out.put("newUsers30d", newUsers30d);
        out.put("totalMoods", totalMoods);
        out.put("conversations", conversations);
        out.put("signupsByDay", signupsByDay);
        return out;
    }
}
