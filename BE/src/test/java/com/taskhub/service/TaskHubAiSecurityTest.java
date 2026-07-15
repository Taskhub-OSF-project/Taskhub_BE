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
