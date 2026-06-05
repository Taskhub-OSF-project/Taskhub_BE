package com.taskhub;

import com.taskhub.dto.SubmittedFileDto;
import com.taskhub.dto.request.SubmissionRequest;
import com.taskhub.dto.response.SubmissionResponse;
import com.taskhub.entity.AcceptanceCriteria;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.SubmissionRepository;
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
class Phase3SubmissionFilesTests {
    @Autowired private SubmissionService submissionService;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignedStudentSubmitOneFileSuccess() {
        User hirer = createUser("phase3-hirer-1@example.com", Role.HIRER);
        User student = createUser("phase3-student-1@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS);
        setAuth(student);
        SubmittedFileDto submittedFile = file(task, student, "work.zip");
        submissionService.precheck(task.getId(), requestWithFiles(List.of(submittedFile)));

        SubmissionResponse response = submissionService.submit(task.getId(), requestWithFiles(List.of(submittedFile)));

        assertNull(response.getFileUrl());
        assertEquals(1, response.getSubmittedFiles().size());
        assertEquals("work.zip", response.getSubmittedFiles().get(0).getFileName());
        assertEquals(TaskStatus.SUBMITTED, taskRepository.findById(task.getId()).orElseThrow().getStatus());
    }

    @Test
    void assignedStudentSubmitMultipleFilesSuccess() {
        User hirer = createUser("phase3-hirer-2@example.com", Role.HIRER);
        User student = createUser("phase3-student-2@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS);
        setAuth(student);
        SubmittedFileDto workFile = file(task, student, "work.zip");
        SubmittedFileDto reportFile = file(task, student, "report.pdf");
        submissionService.precheck(task.getId(), requestWithFiles(List.of(workFile, reportFile)));

        SubmissionResponse response = submissionService.submit(task.getId(), requestWithFiles(List.of(
                workFile,
                reportFile
        )));

        assertEquals(2, response.getSubmittedFiles().size());
        assertEquals(1, submissionRepository.findByTaskId(task.getId()).size());
    }

    @Test
    void legacyFileUrlWithoutPrecheckFails() {
        User hirer = createUser("phase3-hirer-3@example.com", Role.HIRER);
        User student = createUser("phase3-student-3@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS);
        setAuth(student);

        SubmissionRequest req = SubmissionRequest.builder()
                .fileUrl("https://storage.example.com/work.zip")
                .notes("work file application deliverable")
                .build();

        assertThrows(TaskHubException.class, () -> submissionService.submit(task.getId(), req));
    }

    @Test
    void missingFilesAndLegacyUrlFails() {
        User hirer = createUser("phase3-hirer-4@example.com", Role.HIRER);
        User student = createUser("phase3-student-4@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS);
        setAuth(student);

        SubmissionRequest req = SubmissionRequest.builder()
                .notes("work file application deliverable")
                .build();

        assertThrows(TaskHubException.class, () -> submissionService.submit(task.getId(), req));
    }

    @Test
    void submittedFileMissingPathFails() {
        User hirer = createUser("phase3-hirer-5@example.com", Role.HIRER);
        User student = createUser("phase3-student-5@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS);
        setAuth(student);

        SubmittedFileDto file = file(task, student, "work.zip");
        file.setPath(null);

        assertThrows(TaskHubException.class,
                () -> submissionService.submit(task.getId(), requestWithFiles(List.of(file))));
    }

    @Test
    void unsupportedContentTypeFails() {
        User hirer = createUser("phase3-hirer-6@example.com", Role.HIRER);
        User student = createUser("phase3-student-6@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS);
        setAuth(student);

        SubmittedFileDto file = file(task, student, "work.exe");
        file.setContentType("application/x-msdownload");

        assertThrows(TaskHubException.class,
                () -> submissionService.submit(task.getId(), requestWithFiles(List.of(file))));
    }

    @Test
    void studentNotAssignedCannotSubmit() {
        User hirer = createUser("phase3-hirer-7@example.com", Role.HIRER);
        User assigned = createUser("phase3-student-7a@example.com", Role.STUDENT);
        User other = createUser("phase3-student-7b@example.com", Role.STUDENT);
        Task task = createTask(hirer, assigned, TaskStatus.IN_PROGRESS);
        setAuth(other);

        assertThrows(TaskHubException.class,
                () -> submissionService.submit(task.getId(), requestWithFiles(List.of(file(task, other, "work.zip")))));
    }

    @Test
    void hirerCannotSubmit() {
        User hirer = createUser("phase3-hirer-8@example.com", Role.HIRER);
        User student = createUser("phase3-student-8@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS);
        setAuth(hirer);

        assertThrows(TaskHubException.class,
                () -> submissionService.submit(task.getId(), requestWithFiles(List.of(file(task, student, "work.zip")))));
    }

    @Test
    void taskNotInProgressCannotSubmit() {
        User hirer = createUser("phase3-hirer-9@example.com", Role.HIRER);
        User student = createUser("phase3-student-9@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.ACTIVE);
        setAuth(student);

        assertThrows(TaskHubException.class,
                () -> submissionService.submit(task.getId(), requestWithFiles(List.of(file(task, student, "work.zip")))));
    }

    @Test
    void responseReturnsSubmittedFilesMetadata() {
        User hirer = createUser("phase3-hirer-10@example.com", Role.HIRER);
        User student = createUser("phase3-student-10@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.IN_PROGRESS);
        SubmittedFileDto submittedFile = file(task, student, "work.zip");
        setAuth(student);
        submissionService.precheck(task.getId(), requestWithFiles(List.of(submittedFile)));

        SubmissionResponse response = submissionService.submit(task.getId(), requestWithFiles(List.of(submittedFile)));

        assertEquals(submittedFile.getPath(), response.getSubmittedFiles().get(0).getPath());
        assertEquals(submittedFile.getContentType(), response.getSubmittedFiles().get(0).getContentType());
        assertEquals(submittedFile.getSize(), response.getSubmittedFiles().get(0).getSize());
    }

    private SubmissionRequest requestWithFiles(List<SubmittedFileDto> files) {
        return SubmissionRequest.builder()
                .submittedFiles(files)
                .notes("work file application deliverable")
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

    private Task createTask(User hirer, User student, TaskStatus status) {
        Task task = Task.builder()
                .title("Sample submission task")
                .description("Deliver work zip file application package")
                .budget(new BigDecimal("1000"))
                .deadline(LocalDateTime.now().plusDays(3))
                .status(status)
                .hirer(hirer)
                .assignedTo(student)
                .build();
        task.getAcceptanceCriteria().add(AcceptanceCriteria.builder()
                .description("work file application deliverable")
                .task(task)
                .build());
        return taskRepository.save(task);
    }
}
