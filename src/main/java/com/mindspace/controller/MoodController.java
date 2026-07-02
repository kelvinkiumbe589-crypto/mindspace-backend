package com.mindspace.controller;

import com.mindspace.dto.MoodDto;
import com.mindspace.service.AIInsightService;
import com.mindspace.service.MoodService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/moods")
public class MoodController {

    private final MoodService moodService;
    private final AIInsightService aiInsightService;

    public MoodController(MoodService moodService, AIInsightService aiInsightService) {
        this.moodService = moodService;
        this.aiInsightService = aiInsightService;
    }

    // POST /api/moods
    @PostMapping
    public ResponseEntity<MoodDto.MoodResponse> logMood(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MoodDto.MoodRequest request) {
        return ResponseEntity.ok(moodService.logMood(userDetails.getUsername(), request));
    }

    // GET /api/moods/me
    @GetMapping("/me")
    public ResponseEntity<List<MoodDto.MoodResponse>> getMyMoods(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(moodService.getMyMoods(userDetails.getUsername()));
    }

    // POST /api/moods/{id}/insight  ← NEW: triggers Claude AI
    @PostMapping("/{id}/insight")
    public ResponseEntity<MoodDto.MoodResponse> getInsight(@PathVariable UUID id) {
        return ResponseEntity.ok(aiInsightService.generateInsight(id));
    }

    // DELETE /api/moods/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMood(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        moodService.deleteMood(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
