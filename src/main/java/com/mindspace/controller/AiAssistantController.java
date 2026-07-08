package com.mindspace.controller;

import com.mindspace.dto.AiChatDto;
import com.mindspace.service.AIInsightService;
import com.mindspace.service.ReferralService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Stateless AI assistant for the dashboard. Public (no auth) so it works with
 * the current localStorage-based frontend: the client sends a summary of the
 * user's recent moods plus an optional question, and gets a Gemini reply.
 */
@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {

    private final AIInsightService aiInsightService;
    private final ReferralService referralService;

    public AiAssistantController(AIInsightService aiInsightService, ReferralService referralService) {
        this.aiInsightService = aiInsightService;
        this.referralService = referralService;
    }

    // POST /api/ai/chat
    @PostMapping("/chat")
    public ResponseEntity<AiChatDto.ChatResponse> chat(@RequestBody AiChatDto.ChatRequest request) {
        String reply = aiInsightService.assistantReply(request.getMoodContext(), request.getQuestion(), request.getHistory());
        return ResponseEntity.ok(new AiChatDto.ChatResponse(reply));
    }

    // POST /api/ai/deep-dive — authenticated; spends one referral AI credit for a
    // richer personalised analysis.
    @PostMapping("/deep-dive")
    public ResponseEntity<Map<String, Object>> deepDive(@AuthenticationPrincipal UserDetails user,
                                                        @RequestBody AiChatDto.ChatRequest request) {
        if (user == null) return ResponseEntity.status(401).build();
        if (!referralService.spendCredit(user.getUsername())) {
            return ResponseEntity.ok(Map.of("reply", "", "creditsLeft", 0, "outOfCredits", true));
        }
        String reply = aiInsightService.deepDive(request.getMoodContext());
        return ResponseEntity.ok(Map.of("reply", reply, "creditsLeft", referralService.creditsOf(user.getUsername())));
    }
}
