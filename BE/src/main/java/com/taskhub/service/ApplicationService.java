package com.taskhub.service;

import com.taskhub.dto.request.ApplicationRequest;
import com.taskhub.dto.response.ApplicationResponse;
import com.taskhub.dto.response.TaskResponse;
import com.taskhub.entity.*;
import com.taskhub.enums.*;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.*;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
/**
 * Service xử lý nghiệp vụ ứng tuyển task.
 * Thuộc module Application, được gọi từ ApplicationController.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final TaskApplicationRepository appRepo;
    private final TaskRepository taskRepo;
    private final TaskService taskService;

    /**
     * Student apply vào một task đang ACTIVE.
     * Input: taskId và ApplicationRequest.
     * Output: ApplicationResponse vừa tạo.
     */
    @Transactional
    public ApplicationResponse apply(Long taskId, ApplicationRequest req) {
        User student = AuthUtil.getCurrentUser();
        // Chỉ STUDENT được apply.
        if (student.getRole() != Role.STUDENT)
            throw TaskHubException.forbidden("Only students can apply");

        Task task = taskService.findTask(taskId);
        if (task.getStatus() != TaskStatus.ACTIVE)
            throw TaskHubException.badRequest("Task is not accepting applications");
        if (appRepo.existsByTaskIdAndStudentId(taskId, student.getId()))
            throw TaskHubException.badRequest("Already applied");

        TaskApplication app = TaskApplication.builder()
                .task(task).student(student).coverLetter(req.getCoverLetter()).build();
        return toResponse(appRepo.save(app));
    }

    /**
     * Hirer chấp nhận một application.
     * Rule: task -> IN_PROGRESS, assign student, các đơn khác REJECTED.
     */
    @Transactional
    public void acceptApplication(Long applicationId) {
        User hirer = AuthUtil.getCurrentUser();
        TaskApplication app = appRepo.findById(applicationId)
                .orElseThrow(() -> TaskHubException.notFound("Application not found"));
        Task task = app.getTask();

        // Chỉ hirer owner được chấp nhận.
        if (!task.getHirer().getId().equals(hirer.getId()))
            throw TaskHubException.forbidden("Not your task");
        if (task.getStatus() != TaskStatus.ACTIVE)
            throw TaskHubException.badRequest("Task is not ACTIVE");

        app.setStatus(ApplicationStatus.ACCEPTED);
        appRepo.save(app);

        task.setAssignedTo(app.getStudent());
        taskService.transition(task, TaskStatus.IN_PROGRESS);
        taskRepo.save(task);

        // Từ chối các đơn còn lại để tránh nhiều assignee.
        appRepo.findByTaskId(task.getId()).stream()
                .filter(a -> !a.getId().equals(applicationId))
                .forEach(a -> { a.setStatus(ApplicationStatus.REJECTED); appRepo.save(a); });
    }

    /**
     * Danh sách application của một task.
     */
    public List<ApplicationResponse> getTaskApplications(Long taskId) {
        return appRepo.findByTaskId(taskId).stream().map(this::toResponse).toList();
    }

    /**
     * Danh sách application của student hiện tại.
     */
    public List<ApplicationResponse> getMyApplications() {
        return appRepo.findByStudentId(AuthUtil.getCurrentUser().getId())
                .stream().map(this::toResponse).toList();
    }

    /**
     * Danh sách task đã apply nhưng chưa được chọn (PENDING).
     */
    public List<TaskResponse> getMyAppliedTasks() {
        User student = AuthUtil.getCurrentUser();
        if (student.getRole() != Role.STUDENT)
            throw TaskHubException.forbidden("Only students can view applied tasks");

        return appRepo.findByStudentIdAndStatus(student.getId(), ApplicationStatus.PENDING)
                .stream().map(TaskApplication::getTask)
                .map(taskService::toResponse)
                .toList();
    }

    private ApplicationResponse toResponse(TaskApplication a) {
        // Mapping entity -> DTO, không expose User entity trực tiếp.
        return ApplicationResponse.builder()
                .id(a.getId()).taskId(a.getTask().getId())
                .studentId(a.getStudent().getId()).studentName(a.getStudent().getFullName())
                .studentUniversity(a.getStudent().getUniversity())
                .studentMajor(a.getStudent().getMajor())
                .coverLetter(a.getCoverLetter()).status(a.getStatus()).appliedAt(a.getAppliedAt()).build();
    }
}
