package com.taskhub.controller;

import com.taskhub.dto.request.*;
import com.taskhub.dto.response.*;
import com.taskhub.security.AuthUtil;
import com.taskhub.service.TaskHubAiService;
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

    private final TaskHubAiService aiService;

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

    @PostMapping("/criteria/from-job")
    public ResponseEntity<AiCriteriaResponse> suggestCriteriaFromJob(
            @Valid @RequestBody AiCriteriaFromJobRequest request) {
        return ResponseEntity.ok(aiService.suggestCriteriaFromJob(request));
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

    /**
     * Public chat endpoint for non-authenticated users (landing page chatbot).
     * No session storage - just a simple Q&A about TaskHub.
     */
    @PostMapping("/public/chat")
    public ResponseEntity<AiChatResponse> publicChat(
            @Valid @RequestBody PublicAiChatRequest request) {
        String reply = aiService.publicChat(request.getMessage().trim());
        return ResponseEntity.ok(AiChatResponse.builder()
                .reply(reply)
                .sessionType("PUBLIC")
                .responseType("TEXT")
                .timestamp(java.time.LocalDateTime.now())
                .build());
    }

    @GetMapping("/chat/history/{sessionId}")
    public ResponseEntity<List<Map<String, Object>>> getChatHistory(@PathVariable Long sessionId) {
        return ResponseEntity.ok(aiService.getChatHistory(sessionId, AuthUtil.getCurrentUser().getId().toString()));
    }

    @GetMapping("/chat/sessions")
    public ResponseEntity<List<Map<String, Object>>> getUserSessions() {
        return ResponseEntity.ok(aiService.getUserSessions(AuthUtil.getCurrentUser().getId().toString()));
    }

    // ── Task Pricing ────────────────────────────────────────────────────────────

    @PostMapping("/task/price")
    public ResponseEntity<AiPricingResponse> estimateTaskPrice(
            @Valid @RequestBody AiPricingRequest request) {
        return ResponseEntity.ok(aiService.estimateTaskPrice(request));
    }

    // ── Task Auto-Generation ───────────────────────────────────────────────────

    @PostMapping("/task/generate")
    public ResponseEntity<AiGenerateTaskResponse> generateTask(
            @Valid @RequestBody AiGenerateTaskRequest request) {
        return ResponseEntity.ok(aiService.generateTask(request));
    }

    // ── File Extraction ─────────────────────────────────────────────────────────

    @PostMapping("/file/extract")
    public ResponseEntity<AiFileExtractResponse> extractFile(
            @Valid @RequestBody AiFileExtractRequest request) {
        return ResponseEntity.ok(aiService.extractFile(request));
    }
}
