package com.taskhub;

import com.taskhub.dto.SubmittedFileDto;
import com.taskhub.dto.request.RevisionRequest;
import com.taskhub.dto.request.SubmissionRequest;
import com.taskhub.dto.response.RevisionRequestResponse;
import com.taskhub.entity.AcceptanceCriteria;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.RevisionRequestRepository;
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
class Phase35RevisionRequestTests {
    @Autowired private SubmissionService submissionService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RevisionRequestRepository revisionRequestRepository;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hirerOwnerRequestRevisionSucceedsAndMovesTaskToInProgress() {
        SubmittedFixture fixture = createSubmittedTask("phase35-success");
        setAuth(fixture.hirer());

        RevisionRequestResponse response = submissionService.requestRevision(fixture.task().getId(), revisionRequest());

        Task savedTask = taskRepository.findById(fixture.task().getId()).orElseThrow();
        assertNotNull(response.getId());
        assertEquals(fixture.task().getId(), response.getTaskId());
        assertEquals(fixture.submissionFile().getPath(), fixture.submissionFile().getPath());
        assertEquals(1, response.getRevisionNumber());
        assertEquals(TaskStatus.IN_PROGRESS, savedTask.getStatus());
        assertEquals(1, savedTask.getRevisionCount());
        assertEquals(1, revisionRequestRepository.countByTaskId(fixture.task().getId()));
        assertNull(savedTask.getSubmissionAIResultJson());
        assertNull(savedTask.getPrecheckStudentId());
    }

    @Test
    void revisionSuggestionsComeFromPartialAndFailedCriteriaOnly() {
        SubmittedFixture fixture = createSubmittedTask("phase35-suggestions");
        setAuth(fixture.hirer());

        RevisionRequestResponse response = submissionService.requestRevision(fixture.task().getId(), revisionRequest());

        assertEquals(2, response.getAiSuggestions().size());
        assertTrue(response.getAiSuggestions().stream().noneMatch(s -> "MET".equals(s.getStatus())));
        assertTrue(response.getAiSuggestions().stream().anyMatch(s -> "PARTIAL".equals(s.getStatus())));
        assertTrue(response.getAiSuggestions().stream().anyMatch(s -> "FAILED".equals(s.getStatus())));
    }

    @Test
    void allCriteriaMetCannotRequestRevision() {
        SubmittedFixture fixture = createSubmittedTask("phase35-all-met",
                "PNG landing page hero CTA 1920x1080 admin dashboard chart export csv database schema mysql migration");
        setAuth(fixture.hirer());

        TaskHubException ex = assertThrows(TaskHubException.class,
                () -> submissionService.requestRevision(fixture.task().getId(), revisionRequest()));

        assertEquals("All criteria are met. Revision is not recommended.", ex.getMessage());
    }

    @Test
    void studentCannotRequestRevision() {
        SubmittedFixture fixture = createSubmittedTask("phase35-student-denied");
        setAuth(fixture.student());

        assertThrows(TaskHubException.class,
                () -> submissionService.requestRevision(fixture.task().getId(), revisionRequest()));
    }

    @Test
    void otherHirerCannotRequestRevision() {
        SubmittedFixture fixture = createSubmittedTask("phase35-other-hirer-denied");
        User otherHirer = createUser("phase35-other-hirer@example.com", Role.HIRER);
        setAuth(otherHirer);

        assertThrows(TaskHubException.class,
                () -> submissionService.requestRevision(fixture.task().getId(), revisionRequest()));
    }

    @Test
    void taskNotSubmittedCannotRequestRevision() {
        User hirer = createUser("phase35-not-submitted-hirer@example.com", Role.HIRER);
        User student = createUser("phase35-not-submitted-student@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS);
        setAuth(hirer);

        assertThrows(TaskHubException.class, () -> submissionService.requestRevision(task.getId(), revisionRequest()));
    }

    @Test
    void noLatestSubmissionFails() {
        User hirer = createUser("phase35-no-submission-hirer@example.com", Role.HIRER);
        User student = createUser("phase35-no-submission-student@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.SUBMITTED);
        setAuth(hirer);

        TaskHubException ex = assertThrows(TaskHubException.class,
                () -> submissionService.requestRevision(task.getId(), revisionRequest()));

        assertEquals("No latest submission found", ex.getMessage());
    }

    @Test
    void noSubmissionAiResultFails() {
        SubmittedFixture fixture = createSubmittedTask("phase35-no-ai");
        Task task = taskRepository.findById(fixture.task().getId()).orElseThrow();
        task.setSubmissionAIResultJson(null);
        taskRepository.save(task);
        setAuth(fixture.hirer());

        TaskHubException ex = assertThrows(TaskHubException.class,
                () -> submissionService.requestRevision(task.getId(), revisionRequest()));

        assertEquals("Submission AI result is required before requesting revision", ex.getMessage());
    }

    @Test
    void fourthRevisionIsBlocked() {
        SubmittedFixture fixture = createSubmittedTask("phase35-max");
        Task task = taskRepository.findById(fixture.task().getId()).orElseThrow();
        task.setRevisionCount(3);
        taskRepository.save(task);
        setAuth(fixture.hirer());

        TaskHubException ex = assertThrows(TaskHubException.class,
                () -> submissionService.requestRevision(task.getId(), revisionRequest()));

        assertEquals("Maximum revision requests reached. Please dispute or resolve the task.", ex.getMessage());
    }

    @Test
    void revisionHistoryVisibleToOwnerAndAssignedStudentOnly() {
        SubmittedFixture fixture = createSubmittedTask("phase35-history");
        setAuth(fixture.hirer());
        submissionService.requestRevision(fixture.task().getId(), revisionRequest());

        assertEquals(1, submissionService.getRevisionHistory(fixture.task().getId()).size());

        setAuth(fixture.student());
        assertEquals(1, submissionService.getRevisionHistory(fixture.task().getId()).size());

        User other = createUser("phase35-history-other@example.com", Role.STUDENT);
        setAuth(other);
        assertThrows(TaskHubException.class, () -> submissionService.getRevisionHistory(fixture.task().getId()));
    }

    @Test
    void latestReturnsRevisionCountAndLatestRevision() {
        SubmittedFixture fixture = createSubmittedTask("phase35-latest");
        setAuth(fixture.hirer());
        submissionService.requestRevision(fixture.task().getId(), revisionRequest());

        var latest = submissionService.getLatest(fixture.task().getId());

        assertEquals(1, latest.getRevisionCount());
        assertNotNull(latest.getLatestRevision());
        assertEquals(1, latest.getRevisionHistory().size());
    }

    @Test
    void submitAgainAfterRevisionRequiresNewPrecheck() {
        SubmittedFixture fixture = createSubmittedTask("phase35-reprecheck");
        setAuth(fixture.hirer());
        submissionService.requestRevision(fixture.task().getId(), revisionRequest());
        setAuth(fixture.student());

        TaskHubException ex = assertThrows(TaskHubException.class,
                () -> submissionService.submit(fixture.task().getId(), request(List.of(fixture.submissionFile()))));

        assertEquals("Precheck is required before submission", ex.getMessage());
    }

    private SubmittedFixture createSubmittedTask(String prefix) {
        return createSubmittedTask(prefix, "landing page hero CTA dashboard");
    }

    private SubmittedFixture createSubmittedTask(String prefix, String notes) {
        User hirer = createUser(prefix + "-hirer@example.com", Role.HIRER);
        User student = createUser(prefix + "-student@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS);
        SubmittedFileDto submittedFile = file(task, student, "landing-dashboard.zip");
        setAuth(student);
        submissionService.precheck(task.getId(), request(notes, List.of(submittedFile)));
        submissionService.submit(task.getId(), request(notes, List.of(submittedFile)));
        return new SubmittedFixture(taskRepository.findById(task.getId()).orElseThrow(), hirer, student, submittedFile);
    }

    private RevisionRequest revisionRequest() {
        return RevisionRequest.builder()
                .reason("Bai nop con thieu section CTA")
                .description("Vui long bo sung call-to-action va kiem tra lai mau chu dao.")
                .build();
    }

    private SubmissionRequest request(List<SubmittedFileDto> files) {
        return request("landing page hero CTA dashboard", files);
    }

    private SubmissionRequest request(String notes, List<SubmittedFileDto> files) {
        return SubmissionRequest.builder()
                .notes(notes)
                .submittedFiles(files)
                .build();
    }

    private SubmittedFileDto file(Task task, User student, String fileName) {
        return SubmittedFileDto.builder()
                .fileName(fileName)
                .path("submissions/task-" + task.getId() + "/user-" + student.getId() + "/1780580671131-" + fileName)
                .url(null)
                .contentType("application/zip")
                .size(123456L)
                .uploadedAt(LocalDateTime.of(2026, 6, 4, 20, 44, 31))
                .build();
    }

    private Task createTask(User hirer, User student, TaskStatus status) {
        Task task = Task.builder()
                .title("Sample phase 3.5 task")
                .description("Deliver phase 3.5 assets")
                .budget(new BigDecimal("1000"))
                .deadline(LocalDateTime.now().plusDays(3))
                .status(status)
                .hirer(hirer)
                .assignedTo(student)
                .build();
        for (String criterion : List.of(
                "PNG landing page hero CTA 1920x1080",
                "admin dashboard chart export csv",
                "database schema mysql migration"
        )) {
            task.getAcceptanceCriteria().add(AcceptanceCriteria.builder()
                    .description(criterion)
                    .task(task)
                    .build());
        }
        return taskRepository.save(task);
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

    private record SubmittedFixture(Task task, User hirer, User student, SubmittedFileDto submissionFile) {}
}
