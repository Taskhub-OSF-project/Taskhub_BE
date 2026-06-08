package com.taskhub;

import com.taskhub.dto.SubmittedFileDto;
import com.taskhub.dto.request.SubmissionRequest;
import com.taskhub.dto.response.LatestSubmissionResultResponse;
import com.taskhub.dto.response.SubmissionResponse;
import com.taskhub.entity.AcceptanceCriteria;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.service.SubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class Phase34SubmissionLatestTests {
    @Autowired private SubmissionService submissionService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitWithoutPrecheckFails() {
        User hirer = createUser("phase34-hirer-1@example.com", Role.HIRER);
        User student = createUser("phase34-student-1@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS, "work file application deliverable");
        setAuth(student);

        TaskHubException ex = assertThrows(TaskHubException.class,
                () -> submissionService.submit(task.getId(), request(List.of(file(task, student, "work.zip")))));

        assertEquals("Precheck is required before submission", ex.getMessage());
    }

    @Test
    void submitWithPrecheckCanSubmitFalseFails() {
        User hirer = createUser("phase34-hirer-2@example.com", Role.HIRER);
        User student = createUser("phase34-student-2@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS, "database schema mysql migration");
        SubmittedFileDto submittedFile = file(task, student, "report.pdf");
        setAuth(student);

        submissionService.precheck(task.getId(), SubmissionRequest.builder()
                .notes("general report only")
                .submittedFiles(List.of(submittedFile))
                .build());
        TaskHubException ex = assertThrows(TaskHubException.class,
                () -> submissionService.submit(task.getId(), request(List.of(submittedFile))));

        assertEquals("Latest precheck does not allow submission", ex.getMessage());
    }

    @Test
    void submitWithValidPrecheckSucceeds() {
        User hirer = createUser("phase34-hirer-3@example.com", Role.HIRER);
        User student = createUser("phase34-student-3@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS, "work file application deliverable");
        SubmittedFileDto submittedFile = file(task, student, "work.zip");
        setAuth(student);

        submissionService.precheck(task.getId(), request(List.of(submittedFile)));
        SubmissionResponse response = submissionService.submit(task.getId(), request(List.of(submittedFile)));

        assertNotNull(response.getId());
        assertEquals(1, response.getSubmittedFiles().size());
    }

    @Test
    void submitUsesLatestPrecheckInsteadOfLegacyScoreSubmission() {
        User hirer = createUser("phase34-hirer-3b@example.com", Role.HIRER);
        User student = createUser("phase34-student-3b@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS,
                "a a a a a a a a a landing hero");
        SubmittedFileDto submittedFile = file(task, student, "landing-hero.zip");
        setAuth(student);

        submissionService.precheck(task.getId(), SubmissionRequest.builder()
                .notes("metadata evidence only")
                .submittedFiles(List.of(submittedFile))
                .build());
        SubmissionResponse response = submissionService.submit(task.getId(), SubmissionRequest.builder()
                .notes("submit notes do not repeat criterion keywords")
                .submittedFiles(List.of(submittedFile))
                .build());

        assertNotNull(response.getId());
        assertEquals(100, response.getAiScore());
        assertEquals("Submission meets criteria.", response.getAiReport());
    }

    @Test
    void submitWithFilesChangedAfterPrecheckFails() {
        User hirer = createUser("phase34-hirer-4@example.com", Role.HIRER);
        User student = createUser("phase34-student-4@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS, "work file application deliverable");
        SubmittedFileDto precheckedFile = file(task, student, "work.zip");
        SubmittedFileDto changedFile = file(task, student, "work-v2.zip");
        setAuth(student);

        submissionService.precheck(task.getId(), request(List.of(precheckedFile)));
        TaskHubException ex = assertThrows(TaskHubException.class,
                () -> submissionService.submit(task.getId(), request(List.of(changedFile))));

        assertEquals("Submitted files changed after precheck. Please run precheck again.", ex.getMessage());
    }

    @Test
    void latestEndpointHirerOwnerCanView() {
        User hirer = createUser("phase34-hirer-5@example.com", Role.HIRER);
        User student = createUser("phase34-student-5@example.com", Role.STUDENT);
        Task task = createSubmittedTask(hirer, student);
        setAuth(hirer);

        LatestSubmissionResultResponse response = submissionService.getLatest(task.getId());

        assertEquals(task.getId(), response.getTaskId());
        assertNotNull(response.getLatestSubmission());
        assertNotNull(response.getSubmissionAIResult());
    }

    @Test
    void latestEndpointAssignedStudentCanView() {
        User hirer = createUser("phase34-hirer-6@example.com", Role.HIRER);
        User student = createUser("phase34-student-6@example.com", Role.STUDENT);
        Task task = createSubmittedTask(hirer, student);
        setAuth(student);

        LatestSubmissionResultResponse response = submissionService.getLatest(task.getId());

        assertEquals(task.getId(), response.getTaskId());
        assertNotNull(response.getLatestSubmission());
    }

    @Test
    void latestEndpointOtherUserCannotView() {
        User hirer = createUser("phase34-hirer-7@example.com", Role.HIRER);
        User student = createUser("phase34-student-7@example.com", Role.STUDENT);
        User other = createUser("phase34-student-7b@example.com", Role.STUDENT);
        Task task = createSubmittedTask(hirer, student);
        setAuth(other);

        assertThrows(TaskHubException.class, () -> submissionService.getLatest(task.getId()));
    }

    @Test
    void latestEndpointReturnsLatestSubmissionAndAiResult() {
        User hirer = createUser("phase34-hirer-8@example.com", Role.HIRER);
        User student = createUser("phase34-student-8@example.com", Role.STUDENT);
        Task task = createSubmittedTask(hirer, student);
        setAuth(student);

        LatestSubmissionResultResponse response = submissionService.getLatest(task.getId());

        assertEquals(TaskStatus.SUBMITTED, response.getTaskStatus());
        assertNotNull(response.getLatestSubmission());
        assertNotNull(response.getSubmissionAIResult());
        assertTrue(response.getSubmissionAIResult().isCanSubmit());
    }

    @Test
    void submitSuccessTransitionsTaskToSubmitted() {
        User hirer = createUser("phase34-hirer-9@example.com", Role.HIRER);
        User student = createUser("phase34-student-9@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS, "work file application deliverable");
        SubmittedFileDto submittedFile = file(task, student, "work.zip");
        setAuth(student);

        submissionService.precheck(task.getId(), request(List.of(submittedFile)));
        submissionService.submit(task.getId(), request(List.of(submittedFile)));

        assertEquals(TaskStatus.SUBMITTED, taskRepository.findById(task.getId()).orElseThrow().getStatus());
    }

    private Task createSubmittedTask(User hirer, User student) {
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS, "work file application deliverable");
        SubmittedFileDto submittedFile = file(task, student, "work.zip");
        setAuth(student);
        submissionService.precheck(task.getId(), request(List.of(submittedFile)));
        submissionService.submit(task.getId(), request(List.of(submittedFile)));
        return taskRepository.findById(task.getId()).orElseThrow();
    }

    private SubmissionRequest request(List<SubmittedFileDto> files) {
        return SubmissionRequest.builder()
                .notes("work file application deliverable")
                .submittedFiles(files)
                .build();
    }

    private SubmittedFileDto file(Task task, User student, String fileName) {
        String contentType = fileName.endsWith(".pdf") ? "application/pdf" : "application/zip";
        return SubmittedFileDto.builder()
                .fileName(fileName)
                .path("submissions/task-" + task.getId() + "/user-" + student.getId() + "/1780580671131-" + fileName)
                .url(null)
                .contentType(contentType)
                .size(123456L)
                .uploadedAt(LocalDateTime.of(2026, 6, 4, 20, 44, 31))
                .build();
    }

    private void setAuth(User user) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private User createUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .password("encoded")
                .fullName("Test User")
                .role(role)
                .build());
    }

    private Task createTask(User hirer, User student, TaskStatus status, String criterion) {
        Task task = Task.builder()
                .title("Sample phase 3.4 task")
                .description("Deliver phase 3.4 assets")
                .budget(new BigDecimal("1000"))
                .deadline(LocalDateTime.now().plusDays(3))
                .status(status)
                .hirer(hirer)
                .assignedTo(student)
                .build();
        task.getAcceptanceCriteria().add(AcceptanceCriteria.builder()
                .description(criterion)
                .task(task)
                .build());
        return taskRepository.save(task);
    }
}
