package com.taskhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.dto.request.TaskRemovalRequestDto;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.RemovalReason;
import com.taskhub.enums.RemovalStatus;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.repository.EscrowRepository;
import com.taskhub.repository.NotificationRepository;
import com.taskhub.repository.SubmissionRepository;
import com.taskhub.repository.TaskRemovalRequestRepository;
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

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskRemovalServiceTest {

    @Mock private TaskRemovalRequestRepository removalRepo;
    @Mock private TaskRepository taskRepo;
    @Mock private SubmissionRepository submissionRepo;
    @Mock private EscrowRepository escrowRepo;
    private TaskService taskService;
    @Mock private NotificationRepository notificationRepo;
    @Mock private UserRepository userRepo;
    private NotificationService notificationService;
    @Mock private AiModelClient aiModelClient;

    private TaskRemovalService service;
    private User owner;
    private Task task;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepo, null, null, null, null);
        notificationService = new NotificationService(notificationRepo, userRepo);
        service = new TaskRemovalService(removalRepo, taskRepo, submissionRepo, escrowRepo,
                taskService, null, notificationService, aiModelClient,
                new ObjectMapper().findAndRegisterModules());
        owner = User.builder().id(7L).email("owner@test.com").fullName("Owner")
                .password("x").role(Role.HIRER).build();
        task = Task.builder().id(11L).title("Logo").description("Design")
                .budget(BigDecimal.valueOf(100_000)).hirer(owner).status(TaskStatus.ACTIVE).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner, null));

        when(taskRepo.findById(11L)).thenReturn(Optional.of(task));
        when(removalRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(escrowRepo.findByTaskId(11L)).thenReturn(Optional.empty());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lowRiskRequestCanBeAutoApprovedByAi() {
        when(aiModelClient.generate(any(String.class), anyFloat(), anyInt()))
                .thenReturn("{\"decision\":\"AUTO_APPROVE\",\"riskScore\":10,\"summary\":\"Rủi ro thấp\",\"flags\":[]}");

        var response = service.requestRemoval(11L, TaskRemovalRequestDto.builder()
                .reason(RemovalReason.DUPLICATE).build());

        assertEquals(RemovalStatus.APPROVED, response.getStatus());
        assertEquals(TaskStatus.DRAFT, task.getStatus());
        verify(userRepo).findByRole(Role.ADMIN);
    }

    @Test
    void thirdRequestWithinThirtyDaysIsHeldForAdminWithoutCallingAi() {
        when(removalRepo.countByRequestedByIdAndCreatedAtAfter(any(), any())).thenReturn(2L);

        var response = service.requestRemoval(11L, TaskRemovalRequestDto.builder()
                .reason(RemovalReason.DUPLICATE).build());

        assertEquals(RemovalStatus.PENDING, response.getStatus());
        assertEquals(TaskStatus.REMOVAL_REQUESTED, task.getStatus());
        verify(aiModelClient, never()).generate(any(String.class), anyFloat(), anyInt());
        verify(userRepo).findByRole(Role.ADMIN);
    }
}
