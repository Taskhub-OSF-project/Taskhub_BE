package com.taskhub;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.request.PatchTaskRequest;
import com.taskhub.entity.Task;
import com.taskhub.entity.TaskApplication;
import com.taskhub.entity.User;
import com.taskhub.enums.ApplicationStatus;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.TaskApplicationRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.service.ApplicationService;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class Phase2TaskApplicationTests {
    @Autowired private TaskService taskService;
    @Autowired private ApplicationService applicationService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskApplicationRepository taskApplicationRepository;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void patchDraftTaskSuccess() {
        User hirer = createUser("hirer1@example.com", Role.HIRER);
        Task task = createTask(hirer, TaskStatus.DRAFT);
        setAuth(hirer);

        PatchTaskRequest req = PatchTaskRequest.builder()
                .title("Updated title")
                .build();

        var updated = taskService.updateTask(task.getId(), req);
        assertEquals("Updated title", updated.getTitle());
        assertEquals(TaskStatus.DRAFT, updated.getStatus());
    }

    @Test
    void patchNonDraftTaskFails() {
        User hirer = createUser("hirer2@example.com", Role.HIRER);
        Task task = createTask(hirer, TaskStatus.ACTIVE);
        setAuth(hirer);

        PatchTaskRequest req = PatchTaskRequest.builder()
                .title("Updated title")
                .build();

        assertThrows(TaskHubException.class, () -> taskService.updateTask(task.getId(), req));
    }

    @Test
    void deleteDraftTaskSuccessWhenNoApplications() {
        User hirer = createUser("hirer3@example.com", Role.HIRER);
        Task task = createTask(hirer, TaskStatus.DRAFT);
        setAuth(hirer);

        taskService.deleteTask(task.getId());
        assertTrue(taskRepository.findById(task.getId()).isEmpty());
    }

    @Test
    void deleteDraftTaskFailsWhenHasApplications() {
        User hirer = createUser("hirer4@example.com", Role.HIRER);
        User student = createUser("student1@example.com", Role.STUDENT);
        Task task = createTask(hirer, TaskStatus.DRAFT);
        TaskApplication app = TaskApplication.builder()
                .task(task).student(student).build();
        taskApplicationRepository.save(app);
        setAuth(hirer);

        assertThrows(TaskHubException.class, () -> taskService.deleteTask(task.getId()));
    }

    @Test
    void myTasksFilterByStatus() {
        User hirer = createUser("hirer5@example.com", Role.HIRER);
        createTask(hirer, TaskStatus.DRAFT);
        createTask(hirer, TaskStatus.ACTIVE);
        setAuth(hirer);

        var results = taskService.getMyTasks("DRAFT", PageRequestDto.builder().page(0).size(20).build());
        assertEquals(1, results.getContent().size());
        assertEquals(TaskStatus.DRAFT, results.getContent().get(0).getStatus());
    }

    @Test
    void myAppliedTasksReturnsPendingOnly() {
        User hirer = createUser("hirer6@example.com", Role.HIRER);
        User student = createUser("student2@example.com", Role.STUDENT);

        Task pendingTask = createTask(hirer, TaskStatus.ACTIVE);
        Task acceptedTask = createTask(hirer, TaskStatus.ACTIVE);

        TaskApplication pendingApp = TaskApplication.builder()
                .task(pendingTask).student(student).status(ApplicationStatus.PENDING).build();
        TaskApplication acceptedApp = TaskApplication.builder()
                .task(acceptedTask).student(student).status(ApplicationStatus.ACCEPTED).build();
        taskApplicationRepository.saveAll(List.of(pendingApp, acceptedApp));
        setAuth(student);

        var results = applicationService.getMyAppliedTasks();
        assertEquals(1, results.size());
        assertEquals(pendingTask.getId(), results.get(0).getId());
    }

    private void setAuth(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
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

    private Task createTask(User hirer, TaskStatus status) {
        Task task = Task.builder()
                .title("Sample task")
                .description("Deliver 1 PNG file 1920x1080 px, max 5 MB")
                .budget(new BigDecimal("1000"))
                .deadline(LocalDateTime.now().plusDays(3))
                .status(status)
                .hirer(hirer)
                .build();
        return taskRepository.save(task);
    }
}

