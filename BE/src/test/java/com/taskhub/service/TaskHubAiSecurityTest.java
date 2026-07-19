package com.taskhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.dto.request.AiChatRequest;
import com.taskhub.dto.request.AiCriteriaFromJobRequest;
import com.taskhub.dto.request.AiEvaluationRequest;
import com.taskhub.dto.request.AiFileExtractRequest;
import com.taskhub.dto.request.AiProgressRequest;
import com.taskhub.entity.AiChatSession;
import com.taskhub.entity.Submission;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.AiChatMessageRepository;
import com.taskhub.repository.AiChatSessionRepository;
import com.taskhub.repository.AiCriteriaSuggestionRepository;
import com.taskhub.repository.SubmissionRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskHubAiSecurityTest {

    @Mock private AiModelClient aiModelClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private AiChatSessionRepository sessionRepository;
    @Mock private AiChatMessageRepository messageRepository;
    @Mock private AiCriteriaSuggestionRepository criteriaSuggestionRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private UserRepository userRepository;
    private TaskHubAiService service;

    @BeforeEach
    void setUp() {
        service = new TaskHubAiService(HttpClient.newHttpClient(), aiModelClient, objectMapper,
                sessionRepository, messageRepository, criteriaSuggestionRepository,
                taskRepository, submissionRepository, userRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chat_rejectsSessionOwnedByAnotherUser() {
        when(sessionRepository.findById(9L)).thenReturn(Optional.of(AiChatSession.builder()
                .id(9L).userId("200").sessionType("CHAT").build()));

        TaskHubException error = assertThrows(TaskHubException.class, () -> service.chat(
                AiChatRequest.builder().sessionId(9L).message("hello").build(), "100"));

        assertEquals(403, error.getStatus().value());
        verifyNoInteractions(aiModelClient);
    }

    @Test
    void progress_rejectsNonParticipant() {
        User current = User.builder().id(1L).email("user@test.com").fullName("User")
                .password("x").role(Role.STUDENT).build();
        User owner = User.builder().id(2L).email("owner@test.com").fullName("Owner")
                .password("x").role(Role.HIRER).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(current, null));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(Task.builder().id(5L).hirer(owner).build()));

        TaskHubException error = assertThrows(TaskHubException.class, () ->
                service.analyzeProgress(AiProgressRequest.builder().taskId(5L).build()));

        assertEquals(403, error.getStatus().value());
        verifyNoInteractions(aiModelClient);
    }

    @Test
    void evaluation_rejectsTaskSubmissionMismatch() {
        Task task = Task.builder().id(5L).build();
        Submission submission = Submission.builder().id(7L).task(task).build();
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));

        TaskHubException error = assertThrows(TaskHubException.class, () ->
                service.evaluateSubmission(AiEvaluationRequest.builder()
                        .submissionId(7L).taskId(6L).build()));

        assertEquals(400, error.getStatus().value());
        verifyNoInteractions(aiModelClient);
    }

    @Test
    void fileExtraction_rejectsUnapprovedAndPrivateHostsBeforeDownload() {
        ReflectionTestUtils.setField(service, "additionalFileHosts", "localhost");
        ReflectionTestUtils.setField(service, "maxFileBytes", 1024);

        assertEquals(403, assertThrows(TaskHubException.class, () -> service.extractFile(
                AiFileExtractRequest.builder().fileUrl("https://example.com/a.txt").build()))
                .getStatus().value());
        assertEquals(403, assertThrows(TaskHubException.class, () -> service.extractFile(
                AiFileExtractRequest.builder().fileUrl("https://localhost/a.txt").build()))
                .getStatus().value());
        verifyNoInteractions(aiModelClient);
    }

    @Test
    void chat_rejectsReusingSessionForAnotherTask() {
        when(sessionRepository.findById(9L)).thenReturn(Optional.of(AiChatSession.builder()
                .id(9L).userId("100").sessionType("CRITERIA").taskId("5").build()));

        TaskHubException error = assertThrows(TaskHubException.class, () -> service.chat(
                AiChatRequest.builder().sessionId(9L).taskId(6L).message("tạo tiêu chí").build(), "100"));

        assertEquals(400, error.getStatus().value());
        verifyNoInteractions(aiModelClient);
    }

    @Test
    void imageBrief_returnsOneConsistentDraftWithLinkedCriteria() {
        when(aiModelClient.generateWithImage(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("png"),
                org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("""
                        {"suggestedTitle":"Thiết kế banner","suggestedDescription":"Thiết kế 3 banner PNG 1920x1080px", "suggestedCategory":"Thiết kế","logicallyConsistent":true,"consistencySummary":"Số lượng và định dạng khớp nhau","warnings":[],"criteria":[
                        {"text":"Bàn giao đúng 3 file PNG kích thước 1920x1080px","rationale":"Đủ đầu ra","sourceEvidence":"3 banner PNG","relatedCriteria":[2]},
                        {"text":"Cả 3 file sử dụng hệ màu sRGB và không có watermark","rationale":"Đúng kỹ thuật","sourceEvidence":"sRGB, không watermark","relatedCriteria":[1,3]},
                        {"text":"Nội dung chữ trên 3 banner khớp 100% brief đã cung cấp","rationale":"Đúng nội dung","sourceEvidence":"nội dung trong brief","relatedCriteria":[2]}]}
                        """);

        var response = service.extractTaskBriefFromImage(
                new byte[]{1, 2, 3}, "png", "brief.png", null, null);

        assertEquals("Thiết kế banner", response.getSuggestedTitle());
        assertEquals(3, response.getSuggestions().size());
        assertEquals(List.of(2), response.getSuggestions().get(0).getRelatedCriteria());
        assertEquals(true, response.getLogicallyConsistent());
    }

    @Test
    void textBrief_returnsOneConsistentDraftForAutofill() {
        when(aiModelClient.generate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("""
                        {"suggestedTitle":"Thiết kế banner","suggestedDescription":"Thiết kế 3 banner PNG 1920x1080px","suggestedCategory":"Thiết kế","logicallyConsistent":true,"consistencySummary":"Các trường khớp brief","warnings":[],"criteria":[
                        {"text":"Bàn giao đúng 3 file PNG kích thước 1920x1080px","sourceEvidence":"3 banner PNG 1920x1080","relatedCriteria":[2]},
                        {"text":"Cả 3 file dùng hệ màu sRGB và không có watermark","sourceEvidence":"sRGB, không watermark","relatedCriteria":[1,3]},
                        {"text":"Nội dung trên 3 banner khớp toàn bộ câu chữ trong brief","sourceEvidence":"nội dung brief","relatedCriteria":[2]}]}
                        """);

        var response = service.extractTaskBriefFromText(
                "Thiết kế 3 banner PNG 1920x1080, sRGB, không watermark",
                "TEXT", "brief.txt", null, null);

        assertEquals("Thiết kế banner", response.getSuggestedTitle());
        assertEquals("TEXT", response.getDetectedType());
        assertEquals(3, response.getSuggestions().size());
        assertEquals(List.of(2), response.getSuggestions().get(0).getRelatedCriteria());
    }

    @Test
    void textBrief_acceptsProseWrappedJsonAndStringCriteria() {
        when(aiModelClient.generate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("""
                        Kết quả phân tích brief:
                        {"suggestedTitle":"Bàn giao landing page","suggestedCategory":"Lập trình","criteria":[
                        "Landing page hiển thị đúng trên màn hình từ 360px đến 1440px",
                        "Điểm Lighthouse Performance trên mobile đạt tối thiểu 85 điểm",
                        "Biểu mẫu liên hệ gửi thành công và hiển thị thông báo xác nhận"]}
                        Hãy kiểm tra lại trước khi dùng.
                        """);

        var response = service.extractTaskBriefFromText(
                "Xây landing page responsive có form liên hệ", "DOCUMENT", "brief.docx", null, null);

        assertEquals("Bàn giao landing page", response.getSuggestedTitle());
        assertEquals(3, response.getSuggestions().size());
        assertEquals(
                "Landing page hiển thị đúng trên màn hình từ 360px đến 1440px",
                response.getSuggestions().get(0).getText());
    }

    @Test
    void textBrief_retriesWhenModelFirstReturnsNonJsonProse() {
        when(aiModelClient.generate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("Toi khong the tra ve JSON trong lan dau.")
                .thenReturn("""
                        {"suggestedTitle":"Thiet ke banner","criteria":[
                        {"text":"Ban giao dung 3 file PNG kich thuoc 1920x1080px"},
                        {"text":"Ca 3 file dung he mau sRGB va khong co watermark"},
                        {"text":"Noi dung tren 3 banner khop voi brief da cung cap"}]}
                        """);

        var response = service.extractTaskBriefFromText(
                "Thiet ke 3 banner PNG 1920x1080", "DOCUMENT", "brief.docx", null, null);

        assertEquals(3, response.getSuggestions().size());
        verify(aiModelClient, times(2)).generate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void imageBrief_acceptsRootArrayReturnedByModel() {
        when(aiModelClient.generateWithImage(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("jpeg"),
                org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("""
                        ["Bàn giao đủ 5 ảnh JPG theo đúng kích thước trong brief",
                         "Mỗi ảnh có dung lượng không vượt quá 2 MB và dùng hệ màu sRGB",
                         "Toàn bộ nội dung chữ khớp chính xác với nội dung nguồn được cung cấp"]
                        """);

        var response = service.extractTaskBriefFromImage(
                new byte[]{1, 2, 3}, "jpeg", "scan.pdf", null, null);

        assertEquals(3, response.getSuggestions().size());
    }

    @Test
    void briefKeepsPartialCriteriaInsteadOfReturningEmptyResult() {
        when(aiModelClient.generate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("""
                        {"criteria":[{"text":"Bàn giao đúng một file PDF đã được ký duyệt"}]}
                        """);

        var response = service.extractTaskBriefFromText(
                "Bàn giao một file PDF đã duyệt", "PDF", "brief.pdf", null, null);

        assertEquals(1, response.getSuggestions().size());
        org.junit.jupiter.api.Assertions.assertFalse(response.getWarnings().isEmpty());
    }

    @Test
    void criteriaFromJob_parsesObjectWrappedCriteria() {
        when(aiModelClient.generate(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        {"criteria":[{"name":"File bàn giao","description":"Bàn giao 3 file PNG 1080x1080 không watermark","maxScore":20,"evaluationGuide":"Đủ 3 file là đạt"}]}
                        """);

        var response = service.suggestCriteriaFromJob(AiCriteriaFromJobRequest.builder()
                .jobTitle("Thiết kế logo")
                .jobDescription("Thiết kế bộ logo mạng xã hội")
                .numSuggestions(5)
                .build());

        assertEquals(1, response.getSuggestions().size());
        assertEquals("File bàn giao", response.getSuggestions().get(0).getName());
        assertEquals(20, response.getSuggestions().get(0).getMaxScore());
    }

    @Test
    void progress_returnsFriendlyFieldsInsteadOfRawJson() {
        User owner = User.builder().id(2L).email("owner@test.com").fullName("Owner")
                .password("x").role(Role.HIRER).build();
        Task task = Task.builder().id(5L).title("Logo").hirer(owner).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner, null));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(submissionRepository.findByTaskId(5L)).thenReturn(List.of());
        when(aiModelClient.generate(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        {"assessment":"Tiến độ đang đúng kế hoạch","riskFlags":["Thiếu bản xem trước"],"recommendations":["Gửi bản xem trước hôm nay"]}
                        """);

        var response = service.analyzeProgress(AiProgressRequest.builder().taskId(5L).build());

        assertEquals("Tiến độ đang đúng kế hoạch", response.getAiAnalysis());
        org.junit.jupiter.api.Assertions.assertTrue(response.getRiskFlags().contains("Thiếu bản xem trước"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getRecommendations().contains("Gửi bản xem trước hôm nay"));
    }
}
