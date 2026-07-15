package com.taskhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.dto.request.*;
import com.taskhub.dto.response.*;
import com.taskhub.entity.*;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.*;
import com.taskhub.security.AuthUtil;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskHubAiService {

    private final HttpClient httpClient;
    private final AiModelClient aiModelClient;
    private final ObjectMapper objectMapper;
    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final AiCriteriaSuggestionRepository criteriaSuggestionRepository;
    private final TaskRepository taskRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${app.ai.file-allowed-hosts:}")
    private String additionalFileHosts;

    @Value("${app.ai.file-max-bytes:5242880}")
    private int maxFileBytes;

    // ── Task Progress ──────────────────────────────────────────────────────────

    public AiProgressResponse analyzeProgress(AiProgressRequest request) {
        Task task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> TaskHubException.notFound("Task not found"));
        User currentUser = requireTaskParticipant(task);

        List<Submission> submissions = submissionRepository.findByTaskId(task.getId());

        String prompt = buildProgressPrompt(task, submissions, currentUser.getRole().name());

        String reply = callModel(prompt);
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
        if (task != null) {
            requireTaskOwner(task);
        }

        String prompt = buildCriteriaPrompt(task, request);
        String reply = callModel(prompt);

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

    // ═══════════════════════════════════════════════════════════════════════════
    // CRITERIA FROM JOB DESCRIPTION
    // ═══════════════════════════════════════════════════════════════════════════

    public AiCriteriaResponse suggestCriteriaFromJob(AiCriteriaFromJobRequest request) {
        String prompt = buildCriteriaFromJobPrompt(request);
        String reply = callModel(prompt);
        int num = request.getNumSuggestions() != null ? request.getNumSuggestions() : 5;
        List<AiCriteriaResponse.CriteriaSuggestion> suggestions = parseCriteriaSuggestions(reply, num);

        return AiCriteriaResponse.builder()
                .suggestions(suggestions)
                .reasoning(reply)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private String buildCriteriaFromJobPrompt(AiCriteriaFromJobRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                ## Criteria Suggestion from Job Description

                You are an expert at designing clear, measurable acceptance criteria for freelance tasks.

                Analyze the job description below and generate specific, objective evaluation criteria.
                Each criterion should be:
                - SPECIFIC: clearly states what is expected
                - MEASURABLE: can be objectively verified (file count, format, size, %, deadline, etc.)
                - UNAMBIGUOUS: no room for interpretation disputes
                """);

        sb.append("\nJob title: ").append(request.getJobTitle()).append("\n");
        if (request.getJobDescription() != null && !request.getJobDescription().isBlank()) {
            sb.append("Job description:\n").append(request.getJobDescription()).append("\n");
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            sb.append("Category: ").append(request.getCategory()).append("\n");
        }

        int num = request.getNumSuggestions() != null ? request.getNumSuggestions() : 5;
        sb.append("\n## Output Format\n");
        sb.append("Generate exactly ").append(num).append(" criteria. Return as a JSON array:\n");
        sb.append("""
                [
                  {
                    "name": "short descriptive name",
                    "description": "detailed description of deliverable — include numbers, formats, quantities when possible",
                    "maxScore": 10,
                    "evaluationGuide": "objective scoring method — how to verify this criterion is met"
                  }
                ]
                """);

        sb.append("\n## Scoring Guidance for Users\n");
        sb.append("After evaluation, map percentage of criteria met to star rating:\n");
        sb.append("- 95-100%: ⭐⭐⭐⭐⭐ Xuất sắc\n");
        sb.append("- 85-94%:   ⭐⭐⭐⭐☆ Tốt\n");
        sb.append("- 70-84%:   ⭐⭐⭐☆☆ Khá\n");
        sb.append("- 50-69%:   ⭐⭐☆☆☆ Trung bình\n");
        sb.append("- Below 50%: ⭐☆☆☆☆ Chưa đạt\n");

        sb.append("\nBe specific to the job type (code, design, writing, data entry, etc.) and include measurable thresholds.\n");

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TASK AUTO-GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    public AiGenerateTaskResponse generateTask(AiGenerateTaskRequest request) {
        String prompt = buildGenerateTaskPrompt(request);
        String reply = callModel(prompt);
        AiGenerateTaskResponse response = parseGenerateTaskResponse(reply);
        return AiGenerateTaskResponse.builder()
                .rawAiContent(reply)
                .generatedAt(LocalDateTime.now())
                .warnings(validateGeneratedTask(response))
                .build();
    }

    private String buildGenerateTaskPrompt(AiGenerateTaskRequest request) {
        String lang = "Vietnamese";
        if (request.getLanguage() == AiGenerateTaskRequest.Language.ENGLISH) {
            lang = "English";
        }

        return """
                ## Task Auto-Generation Request

                You are an expert freelance project manager. Based on a brief description, generate a complete task specification.

                ## Brief
                %s

                ## Context
                Category hint: %s
                Language for output: %s

                ## Your Task
                Create a comprehensive task specification from this brief. Return a JSON object with:

                {
                  "title": "Clear, specific task title (max 100 chars)",
                  "description": "Detailed description of the work to be done, written clearly for freelancers. Include scope, deliverables, and context.",
                  "category": "Most appropriate category from: Programming, Design, Writing, Data Entry, Marketing, Video, Audio, Translation, Other",
                  "suggestedBudget": number (estimated fair price in VND for Vietnamese market),
                  "suggestedDeadline": ISO date string (deadline, typically 3-30 days from now),
                  "skillsRequired": ["list of required skills"],
                  "difficultyLevel": "Dễ" | "Trung bình" | "Khó" | "Chuyên gia",
                  "estimatedHours": number (estimated work hours),
                  "estimatedDuration": "e.g. '2-3 ngày', '1 tuần'",
                  "suggestedCriteria": [
                    {
                      "name": "criterion name",
                      "description": "detailed deliverable description with measurable terms",
                      "maxScore": 10,
                      "evaluationGuide": "how to objectively score this criterion"
                    }
                  ]
                }

                ## Rules
                - Title must be clear and specific, not vague
                - Description must cover scope, format, quantity requirements
                - Budget should be realistic for Vietnamese market rates
                - Deadline should match complexity
                - Generate 3-5 suggested criteria, all measurable
                - SkillsRequired should list 2-5 relevant skills
                - suggestedBudget in VND only (no currency symbol)

                Return ONLY the JSON object, no markdown code blocks or explanations.
                """.formatted(
                request.getBrief(),
                request.getCategory() != null ? request.getCategory() : "Not specified",
                lang
        );
    }

    private AiGenerateTaskResponse parseGenerateTaskResponse(String text) {
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

                List<AiCriteriaResponse.CriteriaSuggestion> criteria = new ArrayList<>();
                if (data.get("suggestedCriteria") != null) {
                    for (Map<String, Object> c : (List<Map<String, Object>>) data.get("suggestedCriteria")) {
                        criteria.add(AiCriteriaResponse.CriteriaSuggestion.builder()
                                .name((String) c.get("name"))
                                .description((String) c.get("description"))
                                .maxScore(c.get("maxScore") != null ? ((Number) c.get("maxScore")).intValue() : 10)
                                .evaluationGuide((String) c.get("evaluationGuide"))
                                .build());
                    }
                }

                List<String> skills = new ArrayList<>();
                if (data.get("skillsRequired") != null) {
                    skills = new ArrayList<>((List<String>) data.get("skillsRequired"));
                }

                String deadlineStr = (String) data.get("suggestedDeadline");
                LocalDateTime deadline = null;
                if (deadlineStr != null && !deadlineStr.isBlank()) {
                    try { deadline = LocalDateTime.parse(deadlineStr); } catch (Exception ignored) {}
                }

                return AiGenerateTaskResponse.builder()
                        .title((String) data.get("title"))
                        .description((String) data.get("description"))
                        .category((String) data.get("category"))
                        .suggestedBudget(toBigDecimal(data.get("suggestedBudget")))
                        .suggestedDeadline(deadline)
                        .skillsRequired(skills)
                        .difficultyLevel((String) data.get("difficultyLevel"))
                        .estimatedHours(data.get("estimatedHours") != null ? ((Number) data.get("estimatedHours")).intValue() : null)
                        .estimatedDuration((String) data.get("estimatedDuration"))
                        .suggestedCriteria(criteria)
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to parse generated task JSON: {}", e.getMessage());
        }
        return AiGenerateTaskResponse.builder()
                .title("Vui lòng xem nội dung AI bên dưới")
                .description(text.substring(0, Math.min(500, text.length())))
                .suggestedBudget(java.math.BigDecimal.valueOf(500000))
                .build();
    }

    private List<String> validateGeneratedTask(AiGenerateTaskResponse response) {
        List<String> warnings = new ArrayList<>();
        if (response.getTitle() == null || response.getTitle().isBlank()) {
            warnings.add("Title is missing or empty");
        }
        if (response.getDescription() == null || response.getDescription().length() < 50) {
            warnings.add("Description is too short or missing");
        }
        if (response.getSuggestedBudget() == null || response.getSuggestedBudget().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            warnings.add("Suggested budget is missing or invalid");
        }
        if (response.getSuggestedCriteria() == null || response.getSuggestedCriteria().isEmpty()) {
            warnings.add("No suggested criteria generated");
        }
        return warnings;
    }

    /**
     * Gợi ý tiêu chí từ brief dạng tự do (thường là text trích từ file PDF /
     * DOCX / TXT + mô tả task do người dùng nhập). Không cần task entity.
     */
    public AiCriteriaResponse suggestCriteriaFromBrief(String combinedContext, String fileType, String fileName) {
        String prompt = buildBriefCriteriaPrompt(combinedContext, fileType, fileName);
        String reply = callModel(prompt);
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
                Bạn là chuyên gia thiết kế tiêu chí nghiệm thu cho công việc freelance.

                ## File đã upload: %s (%s)
                Tên file: %s

                ## Nội dung trích từ file / brief:
                %s

                ## Nhiệm vụ
                Phân tích nội dung brief phía trên và trích xuất tối đa 5 tiêu chí nghiệm thu CỤ THỂ và ĐO LƯỜNG ĐƯỢC.

                ## Tiêu chuẩn bắt buộc cho mỗi tiêu chí:

                1. **name**: Tên ngắn gọn, là cụm danh từ mô tả deliverable cụ thể (ví dụ: "Bài viết SEO 1500 từ", "Logo vector SVG", "Dashboard React")

                2. **description**: PHẢI bao gồm TỐI THIỂU 3 trong các thông tin sau:
                   - Số lượng/chữ lượng cụ thể (ví dụ: "1500-2000 từ", "tối thiểu 5 section")
                   - Định dạng file yêu cầu (PDF, DOCX, PNG, SVG, v.v.)
                   - Kích thước/độ phân giải (ví dụ: 1920x1080px, A4)
                   - Deadline/ngày giao (nếu có trong brief)
                   - Yêu cầu kỹ thuật cụ thể (font chữ, màu sắc, framework)
                   - Tiêu chuẩn chất lượng (% hoàn thành, tỷ lệ đạt yêu cầu)
                   - Ràng buộc bắt buộc (không watermark, không Plagiarism)

                3. **maxScore**: Điểm tối đa (10-20, tùy mức quan trọng của tiêu chí)

                4. **evaluationGuide**: CÁCH CHẤM ĐIỂM CỤ THỂ:
                   - Liệt kê các mốc điểm (0%, 50%, 80%, 100%)
                   - Mô tả rõ điều kiện đạt mỗi mốc
                   - VD: "0 điểm: không có file; 5 điểm: có file nhưng thiếu nội dung; 8 điểm: đủ nội dung nhưng chưa đúng format; 10 điểm: đầy đủ và đúng yêu cầu"

                ## QUAN TRỌNG:
                - Nếu brief đề cập số lượng cụ thể (ví dụ: "3 banner", "10 trang"), phải dùng CON SỐ ĐÓ trong criteria
                - Nếu brief đề cập chất lượng (ví dụ: "chuyên nghiệp", "sạch sẽ"), phải định nghĩa rõ "chuyên nghiệp/sạch sẽ" = những tiêu chí nào
                - Nếu brief KHÔNG có thông tin cụ thể, hãy dùng tiêu chí mặc định HỢP LÝ cho loại file %s và ghi rõ trong evaluationGuide

                ## Output Format
                Trả về JSON array với cấu trúc:
                [{"name": "...", "description": "... (CHI TIẾT, có số)", "maxScore": 10, "evaluationGuide": "..."}]

                KHÔNG trả về text giải thích, chỉ trả về JSON array.
                """.formatted(fileType, fileName, fileName, context, fileType);
    }

    // ── File Evaluation ────────────────────────────────────────────────────────

    public AiEvaluationResponse evaluateSubmission(AiEvaluationRequest request) {
        Submission submission = submissionRepository.findById(request.getSubmissionId())
                .orElseThrow(() -> TaskHubException.notFound("Submission not found"));
        Task task = submission.getTask();
        if (task == null) {
            throw TaskHubException.notFound("Task not found");
        }
        if (request.getTaskId() != null && !request.getTaskId().equals(task.getId())) {
            throw TaskHubException.badRequest("Submission does not belong to the requested task");
        }
        requireSubmissionParticipant(submission);

        String prompt = buildEvaluationPrompt(task, submission, request);

        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        body.put("generationConfig", Map.of("temperature", 0.3, "topP", 0.8, "maxOutputTokens", 2048));

        String raw = callModelWithBody(prompt, body);
        return parseEvaluationResponse(raw, submission.getId(), task.getId());
    }

    // ── Dispute Resolution ─────────────────────────────────────────────────────

    public AiDisputeResponse resolveDispute(AiDisputeRequest request) {
        Task task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> TaskHubException.notFound("Task not found"));
        Submission submission = submissionRepository.findById(request.getSubmissionId())
                .orElseThrow(() -> TaskHubException.notFound("Submission not found"));
        if (!submission.getTask().getId().equals(task.getId())) {
            throw TaskHubException.badRequest("Submission does not belong to the requested task");
        }
        requireSubmissionParticipant(submission);

        String prompt = buildDisputePrompt(task, submission, request);

        String reply = callModel(prompt);
        return parseDisputeResponse(reply, task.getId(), submission.getId());
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

    public AiChatResponse chat(AiChatRequest request, String userId) {
        AiChatSession session = resolveSession(request, userId);

        // Save user's current message
        AiChatMessage userMsg = AiChatMessage.builder()
                .sessionId(session.getId())
                .role("USER")
                .content(request.getMessage())
                .attachmentRef(request.getAttachmentUrl())
                .build();
        messageRepository.save(userMsg);

        // Update session message count
        session.setMessageCount(session.getMessageCount() + 1);
        session.setLastActiveAt(LocalDateTime.now());
        sessionRepository.save(session);

        // Rebuild context with updated history (includes system prompt + all messages)
        List<AiChatMessage> history = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        String contextPrompt = buildChatContext(history, request);

        String reply = callModel(contextPrompt);

        AiChatMessage aiMsg = AiChatMessage.builder()
                .sessionId(session.getId())
                .role("AI")
                .content(reply)
                .build();
        messageRepository.save(aiMsg);

        // Update session after AI response
        session.setMessageCount(session.getMessageCount() + 1);
        session.setLastActiveAt(LocalDateTime.now());
        sessionRepository.save(session);

        return AiChatResponse.builder()
                .messageId(aiMsg.getId())
                .sessionId(session.getId())
                .reply(reply)
                .sessionType(session.getSessionType())
                .responseType("TEXT")
                .timestamp(aiMsg.getCreatedAt())
                .build();
    }

    public String publicChat(String message) {
        if (message == null || message.isBlank()) {
            throw TaskHubException.badRequest("Message is required");
        }
        String safeMessage = message.trim();
        if (safeMessage.length() > 2000) {
            throw TaskHubException.badRequest("Message is too long");
        }
        String prompt = """
                MODE = "CHAT"
                Bạn là trợ lý công khai của TaskHub, nền tảng việc làm ngắn hạn cho người trẻ Việt Nam.
                Chỉ trả lời về cách tìm việc, đăng việc, tiêu chí nghiệm thu, escrow, nộp bài và tranh chấp
                trên TaskHub. Không yêu cầu hoặc tiết lộ dữ liệu cá nhân. Nếu câu hỏi ngoài phạm vi, hãy từ
                chối ngắn gọn và hướng người dùng tới bộ phận hỗ trợ. Trả lời thân thiện bằng tiếng Việt.

                Câu hỏi của người dùng:
                """ + safeMessage;
        return callModel(prompt);
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
            map.put("lastActiveAt", s.getLastActiveAt());
            map.put("messageCount", s.getMessageCount());
            map.put("contextSummary", s.getContextSummary());
            return map;
        }).toList();
    }

    // ── Core Model Call ────────────────────────────────────────────────────────

    private String callModel(String prompt) {
        return aiModelClient.generate(prompt);
    }

    private String callModelWithBody(String prompt, Map<String, Object> body) {
        float temperature = 0.3f;
        int maxTokens = 4096;
        Object configValue = body.get("generationConfig");
        if (configValue instanceof Map<?, ?> config) {
            Object temperatureValue = config.get("temperature");
            if (temperatureValue instanceof Number number) {
                temperature = number.floatValue();
            }
            Object maxTokensValue = config.get("maxOutputTokens");
            if (maxTokensValue instanceof Number number) {
                maxTokens = number.intValue();
            }
        }
        return aiModelClient.generate(prompt, temperature, maxTokens);
    }

    // ── Session Management ─────────────────────────────────────────────────────

    private AiChatSession resolveSession(AiChatRequest request, String userId) {
        if (request.getSessionId() != null) {
            AiChatSession session = sessionRepository.findById(request.getSessionId())
                    .orElseThrow(() -> TaskHubException.notFound("Session not found"));
            if (!session.getUserId().equals(userId)) {
                throw TaskHubException.forbidden("Access denied");
            }
            updateSessionActivity(session);
            return session;
        }

        if (request.getSubmissionId() != null) {
            Submission submission = submissionRepository.findById(request.getSubmissionId())
                    .orElseThrow(() -> TaskHubException.notFound("Submission not found"));
            requireSubmissionParticipant(submission);
            if (request.getTaskId() != null
                    && !request.getTaskId().equals(submission.getTask().getId())) {
                throw TaskHubException.badRequest("Submission does not belong to the requested task");
            }
        } else if (request.getTaskId() != null) {
            Task task = taskRepository.findById(request.getTaskId())
                    .orElseThrow(() -> TaskHubException.notFound("Task not found"));
            requireTaskParticipant(task);
        }

        String sessionType = request.getSessionType() != null ? request.getSessionType() : "CHAT";

        // Build user profile snapshot for this session
        String userProfileJson = buildUserProfileSnapshot(userId);
        String userContext = buildUserContextFromProfile(userProfileJson);

        AiChatSession session = AiChatSession.builder()
                .userId(userId)
                .sessionType(sessionType)
                .taskId(request.getTaskId() != null ? request.getTaskId().toString() : null)
                .userProfileJson(userProfileJson)
                .messageCount(0)
                .lastActiveAt(LocalDateTime.now())
                .build();
        session = sessionRepository.save(session);

        String systemPrompt = buildSystemPrompt(sessionType, request.getTaskId(), userContext);
        AiChatMessage systemMsg = AiChatMessage.builder()
                .sessionId(session.getId())
                .role("SYSTEM")
                .content(systemPrompt)
                .build();
        messageRepository.save(systemMsg);

        session.setMessageCount(0);
        session.setLastActiveAt(LocalDateTime.now());
        sessionRepository.save(session);

        return session;
    }

    private void updateSessionActivity(AiChatSession session) {
        session.setLastActiveAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    private String buildUserProfileSnapshot(String userId) {
        try {
            User user = userRepository.findById(Long.parseLong(userId)).orElse(null);
            if (user == null) return "{}";

            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("userId", user.getId());
            profile.put("fullName", user.getFullName());
            profile.put("role", user.getRole() != null ? user.getRole().name() : null);
            profile.put("bio", user.getBio());
            profile.put("university", user.getUniversity());
            profile.put("major", user.getMajor());
            profile.put("skills", user.getSkills() != null ? user.getSkills() : List.of());
            profile.put("title", user.getTitle());
            profile.put("experience", user.getExperience());
            profile.put("certifications", user.getCertifications() != null ? user.getCertifications() : List.of());
            profile.put("languages", user.getLanguages() != null ? user.getLanguages() : List.of());
            profile.put("isVerified", user.getIsVerified());
            profile.put("availability", user.getAvailability());
            profile.put("hourlyRate", user.getHourlyRate());

            return objectMapper.writeValueAsString(profile);
        } catch (Exception e) {
            log.warn("Failed to build user profile snapshot: {}", e.getMessage());
            return "{}";
        }
    }

    private String buildUserContextFromProfile(String userProfileJson) {
        try {
            if (userProfileJson == null || userProfileJson.equals("{}") || userProfileJson.isBlank()) {
                return "User profile not available.";
            }
            Map<String, Object> profile = objectMapper.readValue(userProfileJson, new TypeReference<>() {});

            StringBuilder sb = new StringBuilder();
            sb.append("## Current User Profile\n");

            if (profile.get("fullName") != null) {
                sb.append("- Name: ").append(profile.get("fullName")).append("\n");
            }
            if (profile.get("role") != null) {
                sb.append("- Role: ").append(profile.get("role")).append("\n");
                String role = (String) profile.get("role");
                if ("STUDENT".equals(role)) {
                    sb.append("  → This user is a freelancer/student looking for work\n");
                } else if ("HIRER".equals(role)) {
                    sb.append("  → This user is an employer looking to hire freelancers\n");
                } else if ("ADMIN".equals(role)) {
                    sb.append("  → This user is an administrator\n");
                }
            }
            if (profile.get("title") != null && !((String) profile.get("title")).isBlank()) {
                sb.append("- Professional title: ").append(profile.get("title")).append("\n");
            }
            if (profile.get("bio") != null && !((String) profile.get("bio")).isBlank()) {
                sb.append("- Bio: ").append(profile.get("bio")).append("\n");
            }
            if (profile.get("skills") != null && !((java.util.List<?>) profile.get("skills")).isEmpty()) {
                sb.append("- Skills: ").append(String.join(", ", (java.util.List<String>) profile.get("skills"))).append("\n");
            }
            if (profile.get("university") != null && !((String) profile.get("university")).isBlank()) {
                sb.append("- University: ").append(profile.get("university")).append("\n");
            }
            if (profile.get("major") != null && !((String) profile.get("major")).isBlank()) {
                sb.append("- Major: ").append(profile.get("major")).append("\n");
            }
            if (profile.get("isVerified") != null && Boolean.TRUE.equals(profile.get("isVerified"))) {
                sb.append("- Account: Verified\n");
            }
            if (profile.get("availability") != null && !((String) profile.get("availability")).isBlank()) {
                sb.append("- Availability: ").append(profile.get("availability")).append("\n");
            }
            if (profile.get("experience") != null && !((String) profile.get("experience")).isBlank()) {
                String exp = (String) profile.get("experience");
                sb.append("- Experience: ").append(exp.length() > 200 ? exp.substring(0, 200) + "..." : exp).append("\n");
            }
            if (profile.get("certifications") != null && !((java.util.List<?>) profile.get("certifications")).isEmpty()) {
                sb.append("- Certifications: ").append(String.join(", ", (java.util.List<String>) profile.get("certifications"))).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to build user context: {}", e.getMessage());
            return "User profile context unavailable.";
        }
    }

    // ── Prompt Builders ────────────────────────────────────────────────────────

    private String buildSystemPrompt(String sessionType, Long taskId, String userContext) {
        StringBuilder sb = new StringBuilder();
        sb.append(userContext);
        sb.append("\n\n");
        sb.append("## Your Role\n");
        sb.append("You are TaskHub AI Assistant — a professional, helpful AI for a freelance task management platform.\n\n");
        sb.append("You help users with:\n");
        sb.append("- Task progress monitoring and analysis\n");
        sb.append("- Evaluation criteria suggestion based on task requirements\n");
        sb.append("- File/submission evaluation and feedback (based on % of criteria met → star rating)\n");
        sb.append("- Dispute resolution guidance between employers and freelancers\n");
        sb.append("- General Q&A about tasks, submissions, and platform usage\n");
        sb.append("- Task creation guidance and pricing estimation\n");
        sb.append("- Personalized recommendations based on user's skills and role\n\n");

        sb.append("## Context-Aware Behavior\n");
        sb.append("- If user is a HIRER: suggest task creation, pricing, criteria writing, evaluating submissions\n");
        sb.append("- If user is a STUDENT: suggest available tasks, skill improvement, profile optimization\n");
        sb.append("- If user asks about their wallet or balance, use the profile data above\n");
        sb.append("- If user mentions skills, connect with their listed skills for relevant recommendations\n\n");

        sb.append("## Evaluation Star Rating System\n");
        sb.append("When evaluating submissions, use this star rating based on % of criteria met:\n");
        sb.append("- 95-100% criteria met: ⭐⭐⭐⭐⭐ Xuất sắc\n");
        sb.append("- 85-94% criteria met:   ⭐⭐⭐⭐☆ Tốt\n");
        sb.append("- 70-84% criteria met:   ⭐⭐⭐☆☆ Khá\n");
        sb.append("- 50-69% criteria met:   ⭐⭐☆☆☆ Trung bình\n");
        sb.append("- Below 50%:              ⭐☆☆☆☆ Chưa đạt\n\n");

        sb.append("Be specific, actionable, and fair. When evaluating work, be constructive and detailed.\n");
        sb.append("For disputes, remain neutral and base recommendations on evidence.\n");
        sb.append("Always respond in Vietnamese unless the user writes in another language.\n");

        if (taskId != null) {
            sb.append("\n## Current Task Context\n");
            sb.append("Task ID: ").append(taskId).append(" (relevant information will be provided in the chat)\n");
        }

        return sb.toString();
    }

    private String buildProgressPrompt(Task task, List<Submission> submissions, String userRole) {
        StringBuilder sb = new StringBuilder();
        sb.append("MODE = \"TIEN_DO\"\n");
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
        sb.append("MODE = \"TIEU_CHI\"\n");
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
        sb.append("MODE = \"DANH_GIA\"\n");
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
        sb.append("MODE = \"KHIEU_NAI\"\n");
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
        sb.append("MODE = \"CHAT\"\n\n");

        // Find system prompt (user profile context)
        for (AiChatMessage msg : history) {
            if ("SYSTEM".equals(msg.getRole())) {
                sb.append(msg.getContent()).append("\n\n---\n\n");
                break;
            }
        }

        // Chat history (skip system prompt)
        sb.append("## Conversation History\n\n");
        int count = 0;
        for (AiChatMessage msg : history) {
            if ("SYSTEM".equals(msg.getRole())) continue;
            if (count > 0) { // skip first user message (already included in new message)
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n\n");
            }
            count++;
        }
        sb.append("## New Message from User\n").append(current.getMessage());
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TASK PRICING
    // ═══════════════════════════════════════════════════════════════════════════

    public AiPricingResponse estimateTaskPrice(AiPricingRequest request) {
        String prompt = buildPricingPrompt(request);
        String reply = callModel(prompt);
        return parsePricingResponse(reply, request);
    }

    private String buildPricingPrompt(AiPricingRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Task Pricing Request\n\n");
        sb.append("You are an expert freelancer pricing analyst for the Vietnamese market.\n\n");
        sb.append("Task title: ").append(request.getTaskTitle()).append("\n");
        if (request.getTaskDescription() != null && !request.getTaskDescription().isBlank()) {
            sb.append("Description: ").append(request.getTaskDescription()).append("\n");
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            sb.append("Category: ").append(request.getCategory()).append("\n");
        }
        if (request.getDeadline() != null) {
            sb.append("Deadline: ").append(request.getDeadline()).append("\n");
        }
        sb.append("Complexity: ").append(request.getComplexity() != null ? request.getComplexity() : "MEDIUM").append("\n");
        if (request.getSkillsRequired() != null && !request.getSkillsRequired().isEmpty()) {
            sb.append("Required skills: ").append(String.join(", ", request.getSkillsRequired())).append("\n");
        }
        if (request.getExpectedBudget() != null) {
            sb.append("Client expected budget: ").append(request.getExpectedBudget()).append(" VND\n");
        }

        sb.append("""
            \n## Your Task
            Analyze this task and return a realistic price estimate in Vietnamese Dong (VND) for the Vietnamese freelance market.
            Consider:
            - Complexity level (LOW = simple, MEDIUM = moderate, HIGH = complex/specialized)
            - Deadline urgency (closer deadline = higher price)
            - Skill rarity and market demand
            - Typical Vietnamese freelance rates by category
            Return a JSON object with:
            {
              "minPrice": number (minimum fair price in VND),
              "recommendedPrice": number (best value price in VND),
              "maxPrice": number (maximum for premium/expert work in VND),
              "estimatedHours": number (estimated hours needed),
              "estimatedDuration": string (e.g. "2-3 ngày", "1 tuần"),
              "difficultyLevel": string ("Dễ" | "Trung bình" | "Khó" | "Chuyên gia"),
              "pricingFactors": [list of factors considered],
              "marketAnalysis": string (brief analysis of market rates for this type of work in Vietnam),
              "confidence": number (0.0 to 1.0, how confident you are in this estimate)
            }
            Important:
            - All prices must be in VND (no currency symbol needed)
            - Be realistic for the Vietnamese market — not too cheap, not inflated
            - For HIGH complexity tasks with rare skills, maxPrice can be 3-5x minPrice
            - estimatedHours should match complexity and deadline
            - confidence reflects how much information was provided
            """);

        return sb.toString();
    }

    private AiPricingResponse parsePricingResponse(String text, AiPricingRequest request) {
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
                return AiPricingResponse.builder()
                        .minPrice(toBigDecimal(data.get("minPrice")))
                        .recommendedPrice(toBigDecimal(data.get("recommendedPrice")))
                        .maxPrice(toBigDecimal(data.get("maxPrice")))
                        .currency("VND")
                        .estimatedHours(toDouble(data.get("estimatedHours")))
                        .estimatedDuration((String) data.get("estimatedDuration"))
                        .difficultyLevel((String) data.get("difficultyLevel"))
                        .pricingFactors(data.get("pricingFactors") != null
                                ? new ArrayList<>((List<String>) data.get("pricingFactors"))
                                : List.of())
                        .marketAnalysis((String) data.get("marketAnalysis"))
                        .confidence(toDouble(data.get("confidence")))
                        .generatedAt(LocalDateTime.now())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to parse pricing JSON: {}", e.getMessage());
        }
        return AiPricingResponse.builder()
                .recommendedPrice(java.math.BigDecimal.valueOf(500000))
                .minPrice(java.math.BigDecimal.valueOf(200000))
                .maxPrice(java.math.BigDecimal.valueOf(1000000))
                .currency("VND")
                .estimatedHours(8.0)
                .estimatedDuration("1-2 ngày")
                .difficultyLevel("Trung bình")
                .pricingFactors(List.of("Không đủ thông tin để phân tích chi tiết"))
                .marketAnalysis("Vui lòng xem phản hồi từ AI: " + text.substring(0, Math.min(200, text.length())))
                .confidence(0.3)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private java.math.BigDecimal toBigDecimal(Object value) {
        if (value == null) return java.math.BigDecimal.ZERO;
        if (value instanceof java.math.BigDecimal bd) return bd;
        if (value instanceof Number num) return java.math.BigDecimal.valueOf(num.doubleValue());
        try { return new java.math.BigDecimal(value.toString()); }
        catch (Exception e) { return java.math.BigDecimal.ZERO; }
    }

    private Double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number num) return num.doubleValue();
        try { return Double.parseDouble(value.toString()); }
        catch (Exception e) { return 0.0; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FILE EXTRACTION
    // ═══════════════════════════════════════════════════════════════════════════

    private User requireTaskParticipant(Task task) {
        User current = AuthUtil.getCurrentUser();
        if (current.getRole() == Role.ADMIN
                || task.getHirer().getId().equals(current.getId())
                || (task.getAssignedTo() != null
                    && task.getAssignedTo().getId().equals(current.getId()))) {
            return current;
        }
        throw TaskHubException.forbidden("Only task participants can use AI with this task");
    }

    private User requireTaskOwner(Task task) {
        User current = AuthUtil.getCurrentUser();
        if (current.getRole() == Role.ADMIN
                || task.getHirer().getId().equals(current.getId())) {
            return current;
        }
        throw TaskHubException.forbidden("Only the task owner can modify AI criteria");
    }

    private User requireSubmissionParticipant(Submission submission) {
        User current = AuthUtil.getCurrentUser();
        Task task = submission.getTask();
        if (current.getRole() == Role.ADMIN
                || task.getHirer().getId().equals(current.getId())
                || submission.getStudent().getId().equals(current.getId())) {
            return current;
        }
        throw TaskHubException.forbidden("Only submission participants can use AI with this submission");
    }

    public AiFileExtractResponse extractFile(AiFileExtractRequest request) {
        String fileUrl = request.getFileUrl();
        String fileName = request.getFileName();
        String fileType = request.getFileType();

        if (fileName == null || fileName.isBlank()) {
            fileName = extractFileNameFromUrl(fileUrl);
        }
        if (fileType == null || fileType.isBlank()) {
            fileType = extractFileType(fileName, fileUrl);
        }

        // Download file
        byte[] fileBytes = downloadFile(fileUrl);

        // Extract text
        String extractedText = extractText(fileBytes, fileType, fileName);

        // AI analyze
        String prompt = buildFileExtractPrompt(request, extractedText, fileName, fileType);
        String aiReply = callModelWithBody(prompt, buildFileExtractBody(extractedText, request));

        // Parse AI response
        AiFileExtractResponse parsed = parseFileExtractResponse(aiReply, extractedText, fileName, fileType, request);
        String raw = extractedText.length() > 10000 ? extractedText.substring(0, 10000) : extractedText;
        return AiFileExtractResponse.builder()
                .fileName(fileName)
                .fileType(fileType)
                .purpose(request.getPurpose() != null ? request.getPurpose().name() : "CUSTOM")
                .textLength(extractedText.length())
                .rawText(raw)
                .extractedData(parsed.getExtractedData())
                .summary(parsed.getSummary())
                .language(parsed.getLanguage())
                .qualityScore(parsed.getQualityScore())
                .extractedAt(LocalDateTime.now())
                .build();
    }

    private byte[] downloadFile(String fileUrl) {
        URI uri = validateFileUri(fileUrl);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "*/*")
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 400) {
                resp.body().close();
                throw TaskHubException.badRequest("Cannot download file: HTTP " + resp.statusCode());
            }
            long declaredLength = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (declaredLength > maxFileBytes) {
                resp.body().close();
                throw TaskHubException.badRequest("File exceeds the maximum allowed size");
            }
            try (InputStream body = resp.body()) {
                byte[] bytes = body.readNBytes(maxFileBytes + 1);
                if (bytes.length > maxFileBytes) {
                    throw TaskHubException.badRequest("File exceeds the maximum allowed size");
                }
                return bytes;
            }
        } catch (TaskHubException e) {
            throw e;
        } catch (Exception e) {
            log.warn("File download failed for approved host {}: {}", uri.getHost(), e.getClass().getSimpleName());
            throw TaskHubException.badRequest("Cannot download the requested file");
        }
    }

    private URI validateFileUri(String fileUrl) {
        final URI uri;
        try {
            uri = URI.create(fileUrl);
        } catch (Exception e) {
            throw TaskHubException.badRequest("Invalid file URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw TaskHubException.badRequest("Only HTTPS URLs on approved storage hosts are allowed");
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        Set<String> allowedHosts = new HashSet<>();
        try {
            if (supabaseUrl != null && !supabaseUrl.isBlank()) {
                String configuredHost = URI.create(supabaseUrl).getHost();
                if (configuredHost != null) allowedHosts.add(configuredHost.toLowerCase(Locale.ROOT));
            }
        } catch (Exception e) {
            log.warn("Configured Supabase URL is invalid; external AI file extraction remains disabled");
        }
        if (additionalFileHosts != null) {
            Arrays.stream(additionalFileHosts.split(","))
                    .map(String::trim)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .filter(value -> !value.isBlank())
                    .forEach(allowedHosts::add);
        }
        if (!allowedHosts.contains(host)) {
            throw TaskHubException.forbidden("File host is not approved");
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                byte[] raw = address.getAddress();
                boolean uniqueLocalIpv6 = raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress() || uniqueLocalIpv6) {
                    throw TaskHubException.forbidden("File host resolves to a private network");
                }
            }
        } catch (TaskHubException e) {
            throw e;
        } catch (Exception e) {
            throw TaskHubException.badRequest("Cannot resolve file host");
        }
        return uri;
    }

    private String extractText(byte[] bytes, String fileType, String fileName) {
        String type = (fileType != null ? fileType : "").toLowerCase();
        String name = (fileName != null ? fileName : "").toLowerCase();

        if (type.endsWith("pdf") || name.endsWith(".pdf")) {
            if (!startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                throw TaskHubException.badRequest("File content is not a valid PDF");
            }
            return extractPdfText(bytes);
        }
        if (type.contains("document") || type.contains("docx") || name.endsWith(".docx")) {
            if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
                throw TaskHubException.badRequest("File content is not a valid DOCX document");
            }
            validateZipSafety(bytes);
            return extractDocxText(bytes);
        }
        if (type.contains("text") || type.contains("plain") || type.contains("json")
                || name.matches(".*\\.(txt|csv|json|md)$")) {
            for (int i = 0; i < Math.min(bytes.length, 4096); i++) {
                if (bytes[i] == 0) throw TaskHubException.badRequest("Binary content is not accepted as text");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (type.contains("image") || name.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp)")) {
            throw TaskHubException.badRequest("Image analysis is not supported by this endpoint");
        }
        throw TaskHubException.badRequest("Unsupported file type");
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) return false;
        }
        return true;
    }

    private void validateZipSafety(byte[] bytes) {
        long totalUncompressed = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 1000) throw TaskHubException.badRequest("DOCX contains too many entries");
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    totalUncompressed += read;
                    if (totalUncompressed > 20L * 1024 * 1024) {
                        throw TaskHubException.badRequest("DOCX expands beyond the safe limit");
                    }
                }
            }
        } catch (TaskHubException e) {
            throw e;
        } catch (Exception e) {
            throw TaskHubException.badRequest("Invalid DOCX archive");
        }
    }

    private String extractPdfText(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            if (doc.getNumberOfPages() > 50) {
                throw TaskHubException.badRequest("PDF exceeds the 50-page extraction limit");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            text = text.trim();
            return text.length() > 100_000 ? text.substring(0, 100_000) : text;
        } catch (TaskHubException e) {
            throw e;
        } catch (Exception e) {
            log.warn("PDF extraction failed: {}", e.getClass().getSimpleName());
            throw TaskHubException.badRequest("PDF text extraction failed");
        }
    }

    private String extractDocxText(byte[] bytes) {
        try (XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            String text = sb.toString().trim();
            return text.length() > 100_000 ? text.substring(0, 100_000) : text;
        } catch (Exception e) {
            log.warn("DOCX extraction failed: {}", e.getClass().getSimpleName());
            throw TaskHubException.badRequest("DOCX text extraction failed");
        }
    }

    private String extractFileNameFromUrl(String url) {
        if (url == null || url.isBlank()) return "unknown";
        int lastSlash = url.lastIndexOf('/');
        int lastQuest = url.lastIndexOf('?');
        if (lastSlash < 0) return "unknown";
        String name = url.substring(lastSlash + 1);
        if (lastQuest > lastSlash) name = name.substring(0, lastQuest - lastSlash - 1);
        return name;
    }

    private String extractFileType(String fileName, String fileUrl) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        }
        if (fileUrl != null && fileUrl.contains(".")) {
            int q = fileUrl.indexOf('?');
            String path = q > 0 ? fileUrl.substring(0, q) : fileUrl;
            return path.substring(path.lastIndexOf('.') + 1).toLowerCase();
        }
        return "unknown";
    }

    private Map<String, Object> buildFileExtractBody(String extractedText, AiFileExtractRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", extractedText)))));
        body.put("generationConfig", Map.of("temperature", 0.3, "topP", 0.8, "maxOutputTokens", 4096));
        return body;
    }

    private String buildFileExtractPrompt(AiFileExtractRequest request, String extractedText, String fileName, String fileType) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert at analyzing documents and extracting structured information.\n\n");
        sb.append("File: ").append(fileName).append("\n");
        sb.append("Type: ").append(fileType).append("\n");
        sb.append("Purpose: ").append(request.getPurpose() != null ? request.getPurpose().name() : "CUSTOM").append("\n");

        if (request.getContext() != null && !request.getContext().isBlank()) {
            sb.append("Context: ").append(request.getContext()).append("\n");
        }

        sb.append("\nExtracted text from file:\n").append(extractedText).append("\n\n");

        String purpose = request.getPurpose() != null ? request.getPurpose().name() : "CUSTOM";

        sb.append("""
                Based on the purpose "%s", extract and structure the relevant information.
                Return a JSON object with:
                """.formatted(purpose));

        if ("RESUME".equals(purpose)) {
            sb.append("""
                {
                  "fullName": string,
                  "email": string,
                  "phone": string,
                  "skills": [list of skills],
                  "education": string,
                  "experience": string,
                  "certifications": [list],
                  "summary": brief professional summary,
                  "language": detected language code
                }
                """);
        } else if ("BRIEF".equals(purpose)) {
            sb.append("""
                {
                  "projectTitle": string,
                  "objectives": [list of main objectives],
                  "requirements": [list of requirements],
                  "deliverables": [list of expected deliverables],
                  "constraints": [any constraints or limitations],
                  "targetAudience": string,
                  "summary": brief summary of the brief,
                  "language": detected language code
                }
                """);
        } else if ("SUBMISSION".equals(purpose)) {
            sb.append("""
                {
                  "documentType": string (e.g. report, code, design),
                  "mainSections": [list of sections],
                  "keyContent": string (main content summary),
                  "completeness": "complete" | "partial" | "incomplete",
                  "qualityNotes": string,
                  "summary": brief summary of the submission,
                  "language": detected language code
                }
                """);
        } else {
            sb.append("""
                {
                  "summary": brief summary of the document,
                  "keyPoints": [list of key points],
                  "documentType": string (detected type),
                  "language": detected language code,
                  "completeness": "complete" | "partial" | "incomplete",
                  "qualityScore": "high" | "medium" | "low",
                  "extractedData": { any other structured data you can extract }
                }
                """);
        }

        sb.append("\nRespond in Vietnamese where possible. Return ONLY the JSON object.\n");
        return sb.toString();
    }

    private AiFileExtractResponse parseFileExtractResponse(String aiReply, String extractedText, String fileName, String fileType, AiFileExtractRequest request) {
        try {
            String text = aiReply.trim();
            if (text.startsWith("```")) {
                int start = text.indexOf("```") + 3;
                int end = text.lastIndexOf("```");
                if (end > start) text = text.substring(start, text.indexOf("\n", start)).trim();
                if (text.startsWith("json")) text = text.substring(4).trim();
            }

            if (text.startsWith("{")) {
                Map<String, Object> data = objectMapper.readValue(text, new TypeReference<>() {});
                return AiFileExtractResponse.builder()
                        .extractedData(data)
                        .summary((String) data.get("summary"))
                        .language((String) data.get("language"))
                        .qualityScore((String) data.getOrDefault("qualityScore", "medium"))
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to parse file extract AI response: {}", e.getMessage());
        }
        return AiFileExtractResponse.builder()
                .extractedData(Map.of("rawReply", aiReply))
                .summary("Xem phản hồi AI: " + aiReply.substring(0, Math.min(200, aiReply.length())))
                .language("unknown")
                .qualityScore("medium")
                .build();
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
