package com.mindspace.controller;

import com.mindspace.dto.AiChatDto;
import com.mindspace.service.AIInsightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stateless AI assistant for the dashboard. Public (no auth) so it works with
 * the current localStorage-based frontend: the client sends a summary of the
 * user's recent moods plus an optional question, and gets a Gemini reply.
 */
@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {

    private final AIInsightService aiInsightService;

    public AiAssistantController(AIInsightService aiInsightService) {
        this.aiInsightService = aiInsightService;
    }

    // POST /api/ai/chat
    @PostMapping("/chat")
    public ResponseEntity<AiChatDto.ChatResponse> chat(@RequestBody AiChatDto.ChatRequest request) {
        String reply = aiInsightService.assistantReply(request.getMoodContext(), request.getQuestion(), request.getHistory());
        return ResponseEntity.ok(new AiChatDto.ChatResponse(reply));
    }
}
