package com.taskhub;

import com.taskhub.dto.SubmittedFileDto;
import com.taskhub.dto.request.SubmissionRequest;
import com.taskhub.dto.response.SubmissionAIResult;
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
class Phase3PrecheckTests {
    @Autowired private SubmissionService submissionService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void precheckCreatesAiResultSuccessfully() {
        User hirer = createUser("phase33-hirer-1@example.com", Role.HIRER);
        User student = createUser("phase33-student-1@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS, List.of("PNG landing page hero CTA 1920x1080"));
        setAuth(student);

        SubmissionAIResult result = submissionService.precheck(task.getId(), request(
                "Da hoan thanh landing page hero CTA",
                List.of(file(task, student, "landing-page-1920x1080.png"))
        ));

        assertEquals("PASSED", result.getOverallStatus());
        assertTrue(result.isCanSubmit());
        assertNotNull(result.getEvaluatedAt());
        Task savedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertNotNull(savedTask.getSubmissionAIResultJson());
        assertEquals(student.getId(), savedTask.getPrecheckStudentId());
        assertEquals(Boolean.TRUE, savedTask.getPrecheckCanSubmit());
    }

    @Test
    void zeroCriterionMetCannotSubmit() {
        User hirer = createUser("phase33-hirer-2@example.com", Role.HIRER);
        User student = createUser("phase33-student-2@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS, List.of(
                "database schema mysql migration",
                "unit tests junit coverage"
        ));
        setAuth(student);

        SubmissionAIResult result = submissionService.precheck(task.getId(), request(
                "Da nop bao cao tong hop",
                List.of(file(task, student, "report.pdf"))
        ));

        assertEquals("FAILED", result.getOverallStatus());
        assertFalse(result.isCanSubmit());
    }

    @Test
    void failedMoreThanHalfCannotSubmit() {
        User hirer = createUser("phase33-hirer-3@example.com", Role.HIRER);
        User student = createUser("phase33-student-3@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS, List.of(
                "PNG landing page hero CTA 1920x1080",
                "database schema mysql migration",
                "unit tests junit coverage"
        ));
        setAuth(student);

        SubmissionAIResult result = submissionService.precheck(task.getId(), request(
                "Da hoan thanh landing page hero CTA",
                List.of(file(task, student, "landing-page-1920x1080.png"))
        ));

        assertEquals("FAILED", result.getOverallStatus());
        assertFalse(result.isCanSubmit());
    }

    @Test
    void atLeastOneMetAndFailedAtMostHalfCanSubmit() {
        User hirer = createUser("phase33-hirer-4@example.com", Role.HIRER);
        User student = createUser("phase33-student-4@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS, List.of(
                "PNG landing page hero CTA 1920x1080",
                "admin dashboard chart export csv",
                "database schema mysql migration"
        ));
        setAuth(student);

        SubmissionAIResult result = submissionService.precheck(task.getId(), request(
                "Da hoan thanh landing page hero CTA dashboard",
                List.of(file(task, student, "landing-page-1920x1080.png"))
        ));

        assertEquals("PARTIAL", result.getOverallStatus());
        assertTrue(result.isCanSubmit());
    }

    @Test
    void metCriteriaLockedPartialAndFailedUnlocked() {
        User hirer = createUser("phase33-hirer-5@example.com", Role.HIRER);
        User student = createUser("phase33-student-5@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS, List.of(
                "PNG landing page hero CTA 1920x1080",
                "admin dashboard chart export csv",
                "database schema mysql migration"
        ));
        setAuth(student);

        SubmissionAIResult result = submissionService.precheck(task.getId(), request(
                "Da hoan thanh landing page hero CTA dashboard",
                List.of(file(task, student, "landing-page-1920x1080.png"))
        ));

        assertEquals("MET", result.getCriteriaResults().get(0).getStatus());
        assertTrue(result.getCriteriaResults().get(0).isLocked());
        assertEquals("PARTIAL", result.getCriteriaResults().get(1).getStatus());
        assertFalse(result.getCriteriaResults().get(1).isLocked());
        assertEquals("FAILED", result.getCriteriaResults().get(2).getStatus());
        assertFalse(result.getCriteriaResults().get(2).isLocked());
    }

    @Test
    void studentNotAssignedCannotPrecheck() {
        User hirer = createUser("phase33-hirer-6@example.com", Role.HIRER);
        User assigned = createUser("phase33-student-6a@example.com", Role.STUDENT);
        User other = createUser("phase33-student-6b@example.com", Role.STUDENT);
        Task task = createTask(hirer, assigned, TaskStatus.IN_PROGRESS, List.of("PNG landing page hero CTA 1920x1080"));
        setAuth(other);

        assertThrows(TaskHubException.class,
                () -> submissionService.precheck(task.getId(), request("landing page", List.of(file(task, other, "work.png")))));
    }

    @Test
    void hirerCannotPrecheck() {
        User hirer = createUser("phase33-hirer-7@example.com", Role.HIRER);
        User student = createUser("phase33-student-7@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS, List.of("PNG landing page hero CTA 1920x1080"));
        setAuth(hirer);

        assertThrows(TaskHubException.class,
                () -> submissionService.precheck(task.getId(), request("landing page", List.of(file(task, student, "work.png")))));
    }

    @Test
    void taskNotInProgressCannotPrecheck() {
        User hirer = createUser("phase33-hirer-8@example.com", Role.HIRER);
        User student = createUser("phase33-student-8@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.ACTIVE, List.of("PNG landing page hero CTA 1920x1080"));
        setAuth(student);

        assertThrows(TaskHubException.class,
                () -> submissionService.precheck(task.getId(), request("landing page", List.of(file(task, student, "work.png")))));
    }

    @Test
    void responseContainsCriteriaResultsCanSubmitAndEvaluatedAt() {
        User hirer = createUser("phase33-hirer-9@example.com", Role.HIRER);
        User student = createUser("phase33-student-9@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS, List.of("PNG landing page hero CTA 1920x1080"));
        setAuth(student);

        SubmissionAIResult result = submissionService.precheck(task.getId(), request(
                "landing page hero CTA",
                List.of(file(task, student, "landing-page-1920x1080.png"))
        ));

        assertNotNull(result.getCriteriaResults());
        assertFalse(result.getCriteriaResults().isEmpty());
        assertNotNull(result.getEvaluatedAt());
        assertTrue(result.isCanSubmit());
    }

    private SubmissionRequest request(String notes, List<SubmittedFileDto> files) {
        return SubmissionRequest.builder()
                .notes(notes)
                .submittedFiles(files)
                .build();
    }

    private SubmittedFileDto file(Task task, User student, String fileName) {
        String contentType = fileName.endsWith(".png") ? "image/png" : "application/pdf";
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

    private Task createTask(User hirer, User student, TaskStatus status, List<String> criteria) {
        Task task = Task.builder()
                .title("Sample precheck task")
                .description("Deliver precheck assets")
                .budget(new BigDecimal("1000"))
                .deadline(LocalDateTime.now().plusDays(3))
                .status(status)
                .hirer(hirer)
                .assignedTo(student)
                .build();
        for (String criterion : criteria) {
            task.getAcceptanceCriteria().add(AcceptanceCriteria.builder()
                    .description(criterion)
                    .task(task)
                    .build());
        }
        return taskRepository.save(task);
    }
}
