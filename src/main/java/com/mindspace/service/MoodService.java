package com.mindspace.service;

import com.mindspace.dto.MoodDto;
import com.mindspace.model.MoodEntry;
import com.mindspace.model.User;
import com.mindspace.repository.MoodEntryRepository;
import com.mindspace.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MoodService {

    private final MoodEntryRepository moodEntryRepository;
    private final UserRepository userRepository;

    public MoodService(MoodEntryRepository moodEntryRepository, UserRepository userRepository) {
        this.moodEntryRepository = moodEntryRepository;
        this.userRepository = userRepository;
    }

    public MoodDto.MoodResponse logMood(String email, MoodDto.MoodRequest request) {
        User user = getUser(email);

        MoodEntry entry = MoodEntry.builder()
                .user(user)
                .moodScore(request.getMoodScore())
                .emotions(request.getEmotions())
                .journalText(request.getJournalText())
                .build();

        return toResponse(moodEntryRepository.save(entry));
    }

    public List<MoodDto.MoodResponse> getMyMoods(String email) {
        User user = getUser(email);
        return moodEntryRepository.findByUserOrderByLoggedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MoodDto.MoodResponse saveInsight(UUID moodId, String insight) {
        MoodEntry entry = moodEntryRepository.findById(moodId)
                .orElseThrow(() -> new IllegalArgumentException("Mood entry not found"));
        entry.setAiInsight(insight);
        return toResponse(moodEntryRepository.save(entry));
    }

    public MoodEntry getMoodEntry(UUID moodId) {
        return moodEntryRepository.findById(moodId)
                .orElseThrow(() -> new IllegalArgumentException("Mood entry not found"));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    private MoodDto.MoodResponse toResponse(MoodEntry entry) {
        MoodDto.MoodResponse response = new MoodDto.MoodResponse();
        response.setId(entry.getId());
        response.setMoodScore(entry.getMoodScore());
        response.setEmotions(entry.getEmotions());
        response.setJournalText(entry.getJournalText());
        response.setAiInsight(entry.getAiInsight());
        response.setLoggedAt(entry.getLoggedAt());
        return response;
    }
}
