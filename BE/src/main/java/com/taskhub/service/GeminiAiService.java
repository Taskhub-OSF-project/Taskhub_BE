package com.taskhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.dto.request.*;
import com.taskhub.dto.response.*;
import com.taskhub.entity.*;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiAiService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final AiCriteriaSuggestionRepository criteriaSuggestionRepository;
    private final TaskRepository taskRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Value("${app.gemini.model:gemini-2.0-flash}")
    private String model;

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    // ── Task Progress ──────────────────────────────────────────────────────────

    public AiProgressResponse analyzeProgress(AiProgressRequest request) {
        Task task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> TaskHubException.notFound("Task not found"));

        List<Submission> submissions = submissionRepository.findByTaskId(task.getId());

        String prompt = buildProgressPrompt(task, submissions, request.getUserRole());

        String reply = callGemini(prompt);
        return AiProgressResponse.builder()
                .taskId(task.getId())
                .taskTitle(task.getTitle())
                .currentStatus(task.getStatus().name())
                .progressSummary(summarizeProgress(task, submissions))
                .aiAnalysis(reply)
                .riskFlags(detectRisks(task, submissions))
                .recommendations(generateRecommendations(task, submissions))
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    // ── Criteria Suggestions ───────────────────────────────────────────────────

    public AiCriteriaResponse suggestCriteria(AiCriteriaRequest request) {
        Task task = request.getTaskId() != null
                ? taskRepository.findById(request.getTaskId()).orElse(null)
                : null;

        String prompt = buildCriteriaPrompt(task, request);
        String reply = callGemini(prompt);

        List<AiCriteriaResponse.CriteriaSuggestion> suggestions = parseCriteriaSuggestions(reply, request.getNumSuggestions());

        if (task != null) {
            for (int i = 0; i < suggestions.size(); i++) {
                AiCriteriaSuggestion entity = AiCriteriaSuggestion.builder()
                        .taskId(task.getId())
                        .criteriaName(suggestions.get(i).getName())
                        .criteriaDescription(suggestions.get(i).getDescription())
                        .maxScore(suggestions.get(i).getMaxScore())
                        .evaluationGuide(suggestions.get(i).getEvaluationGuide())
                        .isActive(true)
                        .orderIndex(i)
                        .build();
                criteriaSuggestionRepository.save(entity);
            }
        }

        return AiCriteriaResponse.builder()
                .suggestions(suggestions)
                .reasoning(reply)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Gợi ý tiêu chí từ brief dạng tự do (thường là text trích từ file PDF /
     * DOCX / TXT + mô tả task do người dùng nhập). Không cần task entity.
     */
    public AiCriteriaResponse suggestCriteriaFromBrief(String combinedContext, String fileType, String fileName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new TaskHubException("Gemini API key not configured",
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
        String prompt = buildBriefCriteriaPrompt(combinedContext, fileType, fileName);
        String reply = callGemini(prompt);
        int num = 5;
        List<AiCriteriaResponse.CriteriaSuggestion> suggestions = parseCriteriaSuggestions(reply, num);
        return AiCriteriaResponse.builder()
                .suggestions(suggestions)
                .reasoning(reply)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private String buildBriefCriteriaPrompt(String context, String fileType, String fileName) {
        return """
                You are an expert at extracting acceptance criteria from Vietnamese task briefs.

                ## File detected: %s (%s)
                Filename: %s

                ## Brief / description (đã gộp nội dung trích từ file + mô tả do hirer nhập):
                %s

                ## Yêu cầu
                Trích ra tối đa 5 tiêu chí nghiệm thu CỤ THỂ, ĐO LƯỜNG ĐƯỢC, dựa trên brief phía trên.

                Mỗi tiêu chí phải có:
                - name: ngắn gọn, là danh từ nghiệm vụ
                - description: mô tả CHI TIẾT deliverable — bao gồm số lượng, định dạng file, kích thước, deadline, hoặc % đạt yêu cầu (nếu brief đề cập)
                - maxScore: tối đa (mặc định 10, hoặc 20 nếu tiêu chí nặng)
                - evaluationGuide: cách chấm điểm (objective)

                Nếu brief quá ngắn / không đủ thông tin, hãy đưa ra các tiêu chí mặc định hợp lý cho loại file %s và ghi rõ trong evaluationGuide "sử dụng thông tin mặc định vì brief thiếu chi tiết".

                Trả về JSON array: [{name, description, maxScore, evaluationGuide}].
                """.formatted(fileType, fileName, fileName, context, fileType);
    }

    // ── File Evaluation ────────────────────────────────────────────────────────

    public AiEvaluationResponse evaluateSubmission(AiEvaluationRequest request) {
        Submission submission = submissionRepository.findById(request.getSubmissionId())
                .orElseThrow(() -> TaskHubException.notFound("Submission not found"));
        Task task = request.getTaskId() != null
                ? taskRepository.findById(request.getTaskId()).orElse(submission.getTask())
                : submission.getTask();
        if (task == null) {
            throw TaskHubException.notFound("Task not found");
        }

        String prompt = buildEvaluationPrompt(task, submission, request);

        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        body.put("generationConfig", Map.of("temperature", 0.3, "topP", 0.8, "maxOutputTokens", 2048));

        String raw = callGeminiWithBody(prompt, body);
        return parseEvaluationResponse(raw, submission.getId(), task.getId());
    }

    // ── Dispute Resolution ─────────────────────────────────────────────────────

    public AiDisputeResponse resolveDispute(AiDisputeRequest request) {
        Task task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> TaskHubException.notFound("Task not found"));
        Submission submission = submissionRepository.findById(request.getSubmissionId())
                .orElseThrow(() -> TaskHubException.notFound("Submission not found"));

        String prompt = buildDisputePrompt(task, submission, request);

        String reply = callGemini(prompt);
        return parseDisputeResponse(reply, task.getId(), submission.getId());
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

    public AiChatResponse chat(AiChatRequest request, String userId) {
        AiChatSession session = resolveSession(request, userId);

        List<AiChatMessage> history = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        String contextPrompt = buildChatContext(history, request);

        String reply = callGemini(contextPrompt);

        AiChatMessage aiMsg = AiChatMessage.builder()
                .sessionId(session.getId())
                .role("AI")
                .content(reply)
                .build();
        messageRepository.save(aiMsg);

        return AiChatResponse.builder()
                .messageId(aiMsg.getId())
                .sessionId(session.getId())
                .reply(reply)
                .sessionType(session.getSessionType())
                .responseType("TEXT")
                .timestamp(aiMsg.getCreatedAt())
                .build();
    }

    public List<Map<String, Object>> getChatHistory(Long sessionId, String userId) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> TaskHubException.notFound("Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw TaskHubException.forbidden("Access denied");
        }
        List<AiChatMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return messages.stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("role", m.getRole());
            map.put("content", m.getContent());
            map.put("timestamp", m.getCreatedAt());
            return map;
        }).toList();
    }

    public List<Map<String, Object>> getUserSessions(String userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(s -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("sessionType", s.getSessionType());
            map.put("taskId", s.getTaskId());
            map.put("createdAt", s.getCreatedAt());
            return map;
        }).toList();
    }

    // ── Core Gemini Call ───────────────────────────────────────────────────────

    private String callGemini(String prompt) {
        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        body.put("generationConfig", Map.of("temperature", 0.7, "topP", 0.9, "maxOutputTokens", 2048));
        return callGeminiWithBody(prompt, body);
    }

    private String callGeminiWithBody(String prompt, Map<String, Object> body) {
        try {
            if (apiKey == null || apiKey.isBlank()) {
                throw new TaskHubException(
                        "AI service is not configured (APP_GEMINI_API_KEY missing)",
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
            }

            String json = objectMapper.writeValueAsString(body);
            String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
            String url = BASE_URL + "/" + encodedModel + ":generateContent?key=" + apiKey;

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.error("Gemini API error {}: {}", response.statusCode(), response.body());
                throw new TaskHubException(
                        "AI service error: " + response.statusCode() + " — check API key / model",
                        org.springframework.http.HttpStatus.BAD_GATEWAY);
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            return text.isBlank() ? root.path("candidates").path(0).path("content").path("parts").path(0).toString() : text;

        } catch (TaskHubException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gemini call failed", e);
            throw new TaskHubException(
                    "AI service unavailable: " + e.getMessage(),
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    // ── Session Management ─────────────────────────────────────────────────────

    private AiChatSession resolveSession(AiChatRequest request, String userId) {
        if (request.getSessionId() != null) {
            return sessionRepository.findById(request.getSessionId())
                    .orElseThrow(() -> TaskHubException.notFound("Session not found"));
        }

        String sessionType = request.getSessionType() != null ? request.getSessionType() : "CHAT";

        AiChatSession session = AiChatSession.builder()
                .userId(userId)
                .sessionType(sessionType)
                .taskId(request.getTaskId() != null ? request.getTaskId().toString() : null)
                .build();
        session = sessionRepository.save(session);

        String systemPrompt = buildSystemPrompt(sessionType, request.getTaskId());
        AiChatMessage systemMsg = AiChatMessage.builder()
                .sessionId(session.getId())
                .role("SYSTEM")
                .content(systemPrompt)
                .build();
        messageRepository.save(systemMsg);

        AiChatMessage userMsg = AiChatMessage.builder()
                .sessionId(session.getId())
                .role("USER")
                .content(request.getMessage())
                .attachmentRef(request.getAttachmentUrl())
                .build();
        messageRepository.save(userMsg);

        return session;
    }

    // ── Prompt Builders ────────────────────────────────────────────────────────

    private String buildSystemPrompt(String sessionType, Long taskId) {
        return """
                You are TaskHub AI Assistant — a professional, helpful AI for a freelance task management platform.
                You help users with:
                - Task progress monitoring and analysis
                - Evaluation criteria suggestion based on task requirements
                - File/submission evaluation and feedback
                - Dispute resolution guidance between employers and freelancers
                - General Q&A about tasks, submissions, and platform usage

                Be specific, actionable, and fair. When evaluating work, be constructive and detailed.
                For disputes, remain neutral and base recommendations on evidence.
                Always respond in Vietnamese unless the user writes in another language.
                """;
    }

    private String buildProgressPrompt(Task task, List<Submission> submissions, String userRole) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Task Analysis Request\n\n");
        sb.append("Task: ").append(task.getTitle()).append("\n");
        sb.append("Status: ").append(task.getStatus()).append("\n");
        sb.append("Deadline: ").append(task.getDeadline()).append("\n");
        sb.append("Budget: ").append(task.getBudget()).append("\n");
        sb.append("Category: ").append(task.getCategory()).append("\n");
        sb.append("Description: ").append(task.getDescription()).append("\n");
        sb.append("Revision count: ").append(task.getRevisionCount()).append("\n");
        sb.append("Total submissions: ").append(submissions.size()).append("\n");

        if (!submissions.isEmpty()) {
            sb.append("Latest submission: ").append(submissions.get(submissions.size() - 1).getSubmittedAt()).append("\n");
            sb.append("Latest submission notes: ").append(
                    submissions.get(submissions.size() - 1).getNotes() != null
                            ? submissions.get(submissions.size() - 1).getNotes() : "N/A").append("\n");
        }

        sb.append("\nUser role requesting this: ").append(userRole).append("\n\n");
        sb.append("Please provide:\n1. Current progress assessment\n2. Risk flags (overdue, low quality, communication gaps, etc.)\n3. Actionable recommendations\n\nRespond in Vietnamese.");

        return sb.toString();
    }

    private String buildCriteriaPrompt(Task task, AiCriteriaRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Criteria Suggestion Request\n\n");

        if (task != null) {
            sb.append("Task: ").append(task.getTitle()).append("\n");
            sb.append("Category: ").append(task.getCategory()).append("\n");
            sb.append("Description: ").append(task.getDescription()).append("\n");
            sb.append("Budget: ").append(task.getBudget()).append("\n");
            sb.append("Skills required: ").append(String.join(", ", task.getSkillsRequired())).append("\n\n");
        } else if (request.getTaskDescription() != null) {
            sb.append("Task description: ").append(request.getTaskDescription()).append("\n");
            sb.append("Category: ").append(request.getTaskCategory()).append("\n\n");
        }

        sb.append("Please suggest ").append(request.getNumSuggestions() != null ? request.getNumSuggestions() : 5);
        sb.append(" evaluation criteria for this task.\n\n");
        sb.append("For each criterion, provide:\n");
        sb.append("- name: short name\n- description: what to evaluate\n- maxScore: max points (usually 10 or 20)\n- evaluationGuide: how to score this criterion\n\n");
        sb.append("Format as a JSON array with objects containing: name, description, maxScore, evaluationGuide.");

        return sb.toString();
    }

    private String buildEvaluationPrompt(Task task, Submission submission, AiEvaluationRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Submission Evaluation Request\n\n");
        sb.append("Task: ").append(task.getTitle()).append("\n");
        sb.append("Task description: ").append(task.getDescription()).append("\n");
        sb.append("Task category: ").append(task.getCategory()).append("\n");
        sb.append("Deadline: ").append(task.getDeadline()).append("\n");
        sb.append("Submission date: ").append(submission.getSubmittedAt()).append("\n");
        sb.append("Submission notes: ").append(submission.getNotes() != null ? submission.getNotes() : "N/A").append("\n");
        sb.append("File URL: ").append(submission.getFileUrl() != null ? submission.getFileUrl() : "N/A").append("\n");
        sb.append("Is revision: ").append(submission.getIsRevision()).append("\n");
        sb.append("Revision count: ").append(task.getRevisionCount()).append("\n\n");

        if (request.getCustomCriteria() != null) {
            sb.append("Custom criteria to evaluate against:\n").append(request.getCustomCriteria()).append("\n\n");
        }

        sb.append("Please evaluate this submission thoroughly.\n\n");
        sb.append("Return a JSON object with:\n");
        sb.append("- overallAssessment: brief summary\n");
        sb.append("- overallScore: a number from 0-100\n");
        sb.append("- criteriaScores: array of {criterion, score, maxScore, feedback}\n");
        sb.append("- strengths: what was done well\n");
        sb.append("- weaknesses: areas for improvement\n");
        sb.append("- suggestions: actionable feedback\n\n");
        sb.append("Respond in Vietnamese.");

        return sb.toString();
    }

    private String buildDisputePrompt(Task task, Submission submission, AiDisputeRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Dispute Resolution Request\n\n");
        sb.append("Task: ").append(task.getTitle()).append("\n");
        sb.append("Status: ").append(task.getStatus()).append("\n");
        sb.append("Submission: ").append(submission.getNotes()).append("\n");
        sb.append("Submission file: ").append(submission.getFileUrl()).append("\n");
        sb.append("Dispute reason: ").append(request.getDisputeDescription()).append("\n");
        sb.append("Employer claim: ").append(
                request.getEmployerClaim() != null ? request.getEmployerClaim() : "N/A").append("\n");
        sb.append("Freelancer claim: ").append(
                request.getFreelancerClaim() != null ? request.getFreelancerClaim() : "N/A").append("\n\n");

        sb.append("Please analyze this dispute and return a JSON object with:\n");
        sb.append("- disputeSummary: neutral summary of the issue\n");
        sb.append("- aiRecommendation: your recommendation\n");
        sb.append("- fairnessAnalysis: fairness analysis for both parties\n");
        sb.append("- employerScore: 0-10 score for employer fairness\n");
        sb.append("- freelancerScore: 0-10 score for freelancer fairness\n");
        sb.append("- suggestedResolution: a fair resolution proposal\n");
        sb.append("- relevantPrecedents: similar patterns to consider\n\n");
        sb.append("Be neutral and evidence-based. Respond in Vietnamese.");

        return sb.toString();
    }

    private String buildChatContext(List<AiChatMessage> history, AiChatRequest current) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Chat History\n\n");
        for (AiChatMessage msg : history) {
            if ("SYSTEM".equals(msg.getRole())) continue;
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n\n");
        }
        sb.append("## Current Message\n").append(current.getMessage());
        return sb.toString();
    }

    // ── Response Parsers ───────────────────────────────────────────────────────

    private List<AiCriteriaResponse.CriteriaSuggestion> parseCriteriaSuggestions(String text, Integer num) {
        List<AiCriteriaResponse.CriteriaSuggestion> results = new ArrayList<>();
        int count = num != null ? num : 5;

        try {
            text = text.trim();
            if (text.startsWith("```")) {
                int start = text.indexOf("```") + 3;
                int end = text.lastIndexOf("```");
                if (end > start) text = text.substring(start, end).trim();
                if (text.startsWith("json")) text = text.substring(4).trim();
            }

            if (text.startsWith("[")) {
                var list = objectMapper.readValue(text, new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> item : list) {
                    results.add(AiCriteriaResponse.CriteriaSuggestion.builder()
                            .name((String) item.get("name"))
                            .description((String) item.get("description"))
                            .maxScore(item.get("maxScore") != null ? ((Number) item.get("maxScore")).intValue() : 10)
                            .evaluationGuide((String) item.get("evaluationGuide"))
                            .build());
                    if (results.size() >= count) break;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse criteria JSON, falling back to text extraction: {}", e.getMessage());
        }

        if (results.isEmpty()) {
            for (int i = 1; i <= count; i++) {
                results.add(AiCriteriaResponse.CriteriaSuggestion.builder()
                        .name("Tiêu chí " + i)
                        .description("Vui lòng xem phản hồi từ AI: " + text.substring(0, Math.min(200, text.length())))
                        .maxScore(10)
                        .build());
            }
        }
        return results;
    }

    private AiEvaluationResponse parseEvaluationResponse(String text, Long submissionId, Long taskId) {
        try {
            text = text.trim();
            if (text.startsWith("```")) {
                int start = text.indexOf("```") + 3;
                int end = text.lastIndexOf("```");
                if (end > start) text = text.substring(start, end).trim();
                if (text.startsWith("json")) text = text.substring(4).trim();
            }

            if (text.startsWith("{")) {
                Map<String, Object> data = objectMapper.readValue(text, new TypeReference<>() {});
                List<AiEvaluationResponse.CriteriaScore> scores = new ArrayList<>();
                if (data.get("criteriaScores") != null) {
                    for (Map<String, Object> s : (List<Map<String, Object>>) data.get("criteriaScores")) {
                        scores.add(AiEvaluationResponse.CriteriaScore.builder()
                                .criterion((String) s.get("criterion"))
                                .score(s.get("score") != null ? ((Number) s.get("score")).doubleValue() : 0)
                                .maxScore(s.get("maxScore") != null ? ((Number) s.get("maxScore")).doubleValue() : 10)
                                .feedback((String) s.get("feedback"))
                                .build());
                    }
                }
                return AiEvaluationResponse.builder()
                        .submissionId(submissionId)
                        .taskId(taskId)
                        .overallAssessment((String) data.get("overallAssessment"))
                        .overallScore(data.get("overallScore") != null ? ((Number) data.get("overallScore")).doubleValue() : 0)
                        .criteriaScores(scores)
                        .strengths((String) data.get("strengths"))
                        .weaknesses((String) data.get("weaknesses"))
                        .suggestions((String) data.get("suggestions"))
                        .evaluatedAt(LocalDateTime.now())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to parse evaluation JSON: {}", e.getMessage());
        }
        return AiEvaluationResponse.builder()
                .submissionId(submissionId)
                .taskId(taskId)
                .overallAssessment(text)
                .overallScore(0.0)
                .criteriaScores(List.of())
                .evaluatedAt(LocalDateTime.now())
                .build();
    }

    private AiDisputeResponse parseDisputeResponse(String text, Long taskId, Long submissionId) {
        try {
            text = text.trim();
            if (text.startsWith("```")) {
                int start = text.indexOf("```") + 3;
                int end = text.lastIndexOf("```");
                if (end > start) text = text.substring(start, end).trim();
                if (text.startsWith("json")) text = text.substring(4).trim();
            }

            if (text.startsWith("{")) {
                Map<String, Object> data = objectMapper.readValue(text, new TypeReference<>() {});
                return AiDisputeResponse.builder()
                        .taskId(taskId)
                        .submissionId(submissionId)
                        .disputeSummary((String) data.get("disputeSummary"))
                        .aiRecommendation((String) data.get("aiRecommendation"))
                        .fairnessAnalysis((String) data.get("fairnessAnalysis"))
                        .employerScore(data.get("employerScore") != null ? ((Number) data.get("employerScore")).doubleValue() : 5.0)
                        .freelancerScore(data.get("freelancerScore") != null ? ((Number) data.get("freelancerScore")).doubleValue() : 5.0)
                        .suggestedResolution((String) data.get("suggestedResolution"))
                        .relevantPrecedents((String) data.get("relevantPrecedents"))
                        .analyzedAt(LocalDateTime.now())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to parse dispute JSON: {}", e.getMessage());
        }
        return AiDisputeResponse.builder()
                .taskId(taskId)
                .submissionId(submissionId)
                .aiRecommendation(text)
                .employerScore(5.0)
                .freelancerScore(5.0)
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String summarizeProgress(Task task, List<Submission> submissions) {
        String status = task.getStatus().name();
        int total = submissions.size();
        int revisions = (int) submissions.stream().filter(s -> Boolean.TRUE.equals(s.getIsRevision())).count();
        if (task.getDeadline() != null && LocalDateTime.now().isAfter(task.getDeadline())) {
            return "Quá hạn. " + total + " bài nộp, " + revisions + " lần sửa đổi.";
        }
        return status + " - " + total + " bài nộp, " + revisions + " lần sửa đổi.";
    }

    private String detectRisks(Task task, List<Submission> submissions) {
        List<String> risks = new ArrayList<>();
        if (task.getDeadline() != null && LocalDateTime.now().isAfter(task.getDeadline())) {
            risks.add("Quá hạn giao bài");
        }
        if (task.getRevisionCount() >= 3) {
            risks.add("Nhiều lần sửa đổi - có thể có vấn đề về yêu cầu hoặc chất lượng");
        }
        if (submissions.stream().anyMatch(s -> s.getFileUrl() == null)) {
            risks.add("Một số bài nộp không có file đính kèm");
        }
        if (task.getStatus().name().equals("DRAFT") || task.getStatus().name().equals("OPEN")) {
            risks.add("Task chưa được assign - chưa có freelancer");
        }
        return risks.isEmpty() ? "Không có rủi ro nổi bật" : String.join("; ", risks);
    }

    private String generateRecommendations(Task task, List<Submission> submissions) {
        List<String> recs = new ArrayList<>();
        if (task.getRevisionCount() > 0) {
            recs.add("Xem lại mô tả task để đảm bảo freelancer hiểu đúng yêu cầu");
        }
        if (!submissions.isEmpty() && task.getStatus().name().equals("IN_PROGRESS")) {
            recs.add("Xem xét đánh giá bài nộp mới nhất trước deadline");
        }
        if (submissions.isEmpty() && task.getStatus().name().equals("IN_PROGRESS")) {
            recs.add("Liên hệ freelancer để cập nhật tiến độ");
        }
        return recs.isEmpty() ? "Tiến độ tốt, tiếp tục theo dõi." : String.join("; ", recs);
    }
}
