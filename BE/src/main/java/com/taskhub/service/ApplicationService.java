package com.taskhub.service;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.request.ApplicationRequest;
import com.taskhub.dto.response.ApplicationResponse;
import com.taskhub.dto.response.TaskResponse;
import com.taskhub.entity.*;
import com.taskhub.enums.*;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.*;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final TaskApplicationRepository appRepo;
    private final TaskRepository taskRepo;
    private final TaskService taskService;
    private final NotificationService notificationService;

    @Transactional
    public ApplicationResponse apply(Long taskId, ApplicationRequest req) {
        User student = AuthUtil.getCurrentUser();
        if (student.getRole() != Role.STUDENT)
            throw TaskHubException.forbidden("Only students can apply");

        Task task = taskService.findTask(taskId);
        if (task.getStatus() != TaskStatus.ACTIVE)
            throw TaskHubException.badRequest("Task is not accepting applications");
        if (appRepo.existsByTaskIdAndStudentId(taskId, student.getId()))
            throw TaskHubException.badRequest("Already applied");

        TaskApplication app = TaskApplication.builder()
                .task(task).student(student).coverLetter(req.getCoverLetter()).build();
        app = appRepo.save(app);

        // Increment applicant count on task
        task.setApplicantCount((task.getApplicantCount() != null ? task.getApplicantCount() : 0) + 1);
        taskRepo.save(task);

        // Notify hirer of new application
        notificationService.notifyApplicationReceived(
                task.getHirer().getId(),
                student.getFullName(),
                task.getTitle(),
                taskId);

        return toResponse(app);
    }

    @Transactional
    public void acceptApplication(Long applicationId) {
        User hirer = AuthUtil.getCurrentUser();
        TaskApplication app = appRepo.findById(applicationId)
                .orElseThrow(() -> TaskHubException.notFound("Application not found"));
        Task task = app.getTask();

        if (!task.getHirer().getId().equals(hirer.getId()))
            throw TaskHubException.forbidden("Not your task");
        if (task.getStatus() != TaskStatus.ACTIVE)
            throw TaskHubException.badRequest("Task is not ACTIVE");

        app.setStatus(ApplicationStatus.ACCEPTED);
        appRepo.save(app);

        task.setAssignedTo(app.getStudent());
        taskService.transition(task, TaskStatus.IN_PROGRESS);
        taskRepo.save(task);

        // Notify student they were accepted
        notificationService.notifyTaskAssigned(app.getStudent().getId(), task.getTitle(), task.getId());
        notificationService.notifyApplicationAccepted(app.getStudent().getId(), task.getTitle(), task.getId());

        // Reject other applications
        appRepo.findByTaskId(task.getId()).stream()
                .filter(a -> !a.getId().equals(applicationId))
                .forEach(a -> { a.setStatus(ApplicationStatus.REJECTED); appRepo.save(a); });
    }

    public PageResponse<ApplicationResponse> getTaskApplications(Long taskId, PageRequestDto pageReq) {
        Page< TaskApplication> page = appRepo.findByTaskId(taskId,
                org.springframework.data.domain.PageRequest.of(pageReq.getPage(), Math.min(pageReq.getSize(), 100),
                        Sort.by(Sort.Direction.DESC, "appliedAt")));
        return PageResponse.<ApplicationResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    public PageResponse<ApplicationResponse> getMyApplications(PageRequestDto pageReq) {
        User student = AuthUtil.getCurrentUser();
        Page<TaskApplication> page = appRepo.findByStudentId(student.getId(),
                org.springframework.data.domain.PageRequest.of(pageReq.getPage(), Math.min(pageReq.getSize(), 100),
                        Sort.by(Sort.Direction.DESC, "appliedAt")));
        return PageResponse.<ApplicationResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

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
        return ApplicationResponse.builder()
                .id(a.getId()).taskId(a.getTask().getId())
                .studentId(a.getStudent().getId()).studentName(a.getStudent().getFullName())
                .studentUniversity(a.getStudent().getUniversity())
                .studentMajor(a.getStudent().getMajor())
                .coverLetter(a.getCoverLetter()).status(a.getStatus()).appliedAt(a.getAppliedAt()).build();
    }
}
