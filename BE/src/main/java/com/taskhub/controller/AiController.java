package com.taskhub.controller;

import com.taskhub.dto.request.*;
import com.taskhub.dto.response.*;
import com.taskhub.security.AuthUtil;
import com.taskhub.service.GeminiAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final GeminiAiService aiService;

    @PostMapping("/progress")
    public ResponseEntity<AiProgressResponse> analyzeProgress(
            @Valid @RequestBody AiProgressRequest request) {
        return ResponseEntity.ok(aiService.analyzeProgress(request));
    }

    @PostMapping("/criteria/suggest")
    public ResponseEntity<AiCriteriaResponse> suggestCriteria(
            @Valid @RequestBody AiCriteriaRequest request) {
        return ResponseEntity.ok(aiService.suggestCriteria(request));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<AiEvaluationResponse> evaluateSubmission(
            @Valid @RequestBody AiEvaluationRequest request) {
        return ResponseEntity.ok(aiService.evaluateSubmission(request));
    }

    @PostMapping("/dispute")
    public ResponseEntity<AiDisputeResponse> resolveDispute(
            @Valid @RequestBody AiDisputeRequest request) {
        return ResponseEntity.ok(aiService.resolveDispute(request));
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            @Valid @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(aiService.chat(request, AuthUtil.getCurrentUser().getId().toString()));
    }

    @GetMapping("/chat/history/{sessionId}")
    public ResponseEntity<List<Map<String, Object>>> getChatHistory(@PathVariable Long sessionId) {
        return ResponseEntity.ok(aiService.getChatHistory(sessionId, AuthUtil.getCurrentUser().getId().toString()));
    }

    @GetMapping("/chat/sessions")
    public ResponseEntity<List<Map<String, Object>>> getUserSessions() {
        return ResponseEntity.ok(aiService.getUserSessions(AuthUtil.getCurrentUser().getId().toString()));
    }
}
