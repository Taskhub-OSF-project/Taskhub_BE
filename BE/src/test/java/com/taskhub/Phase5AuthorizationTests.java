package com.taskhub;

import com.taskhub.dto.request.DisputeResolveRequest;
import com.taskhub.dto.request.EvaluationRequest;
import com.taskhub.entity.AcceptanceCriteria;
import com.taskhub.entity.Submission;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.SubmissionRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.service.DisputeService;
import com.taskhub.service.EvaluationService;
import com.taskhub.service.FileStorageService;
import com.taskhub.service.SubmissionService;
import com.taskhub.service.TaskService;
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

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class Phase5AuthorizationTests {
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private EvaluationService evaluationService;
    @Autowired private DisputeService disputeService;
    @Autowired private SubmissionService submissionService;
    @Autowired private TaskService taskService;
    @Autowired private FileStorageService fileStorageService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unrelatedUserCannotViewEvaluation() {
        User hirer = user("phase5-eval-hirer@example.com", Role.HIRER);
        User student = user("phase5-eval-student@example.com", Role.STUDENT);
        User outsider = user("phase5-eval-outsider@example.com", Role.STUDENT);
        Task task = task(hirer, student, TaskStatus.SUBMITTED, "criterion");
        Submission submission = submissionRepository.save(Submission.builder()
                .task(task).student(student).notes("work").build());
        authenticate(outsider);

        assertThrows(TaskHubException.class, () -> evaluationService.getEvaluation(submission.getId()));
    }

    @Test
    void evaluationRejectsCriterionFromAnotherTaskBeforeCallingAi() {
        User hirer = user("phase5-owner@example.com", Role.HIRER);
        User student = user("phase5-worker@example.com", Role.STUDENT);
        Task first = task(hirer, student, TaskStatus.SUBMITTED, "first criterion");
        Task second = task(hirer, student, TaskStatus.SUBMITTED, "second criterion");
        Submission submission = submissionRepository.save(Submission.builder()
                .task(first).student(student).notes("work").build());
        Long foreignCriterionId = second.getAcceptanceCriteria().get(0).getId();
        authenticate(hirer);

        EvaluationRequest request = EvaluationRequest.builder()
                .submissionId(submission.getId())
                .criteriaIds(List.of(foreignCriterionId))
                .build();
        assertThrows(TaskHubException.class, () -> evaluationService.evaluateSubmission(request));
    }

    @Test
    void nonAdminCannotResolveDispute() {
        User hirer = user("phase5-dispute-hirer@example.com", Role.HIRER);
        User student = user("phase5-dispute-student@example.com", Role.STUDENT);
        Task task = task(hirer, student, TaskStatus.DISPUTED, "disputed criterion");
        authenticate(hirer);

        DisputeResolveRequest request = new DisputeResolveRequest();
        request.setAction(DisputeResolveRequest.DisputeAction.RELEASE_PAYMENT);
        assertThrows(TaskHubException.class,
                () -> disputeService.adminResolveDispute(task.getId(), request));
    }

    @Test
    void outsiderCannotReadRevisionHistoryOrPrivateDraft() {
        User hirer = user("phase5-private-hirer@example.com", Role.HIRER);
        User assigned = user("phase5-private-student@example.com", Role.STUDENT);
        User outsider = user("phase5-private-outsider@example.com", Role.STUDENT);
        Task task = task(hirer, assigned, TaskStatus.DRAFT, "private criterion");
        authenticate(outsider);

        assertThrows(TaskHubException.class, () -> submissionService.getRevisionHistory(task.getId()));
        assertThrows(TaskHubException.class, () -> taskService.getTask(task.getId()));
    }

    @Test
    void signedUrlRejectsPathOutsideTaskNamespace() {
        authenticate(user("phase5-file-admin@example.com", Role.ADMIN));
        assertThrows(TaskHubException.class,
                () -> fileStorageService.createSignedUrl("../other-user/secret.pdf", 1L));
    }

    private User user(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email).password("encoded").fullName("Phase 5 User").role(role).build());
    }

    private Task task(User hirer, User assigned, TaskStatus status, String criterion) {
        Task task = Task.builder()
                .title("Phase 5 task")
                .description("Authorization test")
                .budget(new BigDecimal("1000"))
                .deadline(LocalDateTime.now().plusDays(2))
                .status(status)
                .hirer(hirer)
                .assignedTo(assigned)
                .build();
        task.getAcceptanceCriteria().add(AcceptanceCriteria.builder()
                .task(task).description(criterion).build());
        return taskRepository.save(task);
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
