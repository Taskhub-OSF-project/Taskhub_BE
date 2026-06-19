package com.taskhub.service;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.response.NotificationResponse;
import com.taskhub.entity.Notification;
import com.taskhub.entity.User;
import com.taskhub.enums.NotificationType;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.NotificationRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final WebSocketPushService pushService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    @Transactional
    public NotificationResponse notify(Long userId, NotificationType type, String title, String message, String actionUrl, Long taskId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl(actionUrl)
                .taskId(taskId)
                .build();

        notification = notificationRepository.save(notification);
        NotificationResponse response = toResponse(notification);

        pushService.pushToUser(userId, "NOTIFICATION", response);
        return response;
    }

    @Transactional
    private NotificationResponse notifyAndEmail(Long userId, NotificationType type, String title, String message,
            String actionUrl, Long taskId, String emailContent) {
        NotificationResponse response = notify(userId, type, title, message, actionUrl, taskId);
        if (emailContent != null) {
            userRepository.findById(userId).ifPresent(u ->
                    emailService.sendApplicationAcceptedEmail(u.getEmail(), u.getFullName(), emailContent, taskId));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(PageRequestDto pageReq) {
        User user = AuthUtil.getCurrentUser();
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(),
                org.springframework.data.domain.PageRequest.of(
                        pageReq.getPage(),
                        Math.min(pageReq.getSize(), 50),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.<NotificationResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications() {
        User user = AuthUtil.getCurrentUser();
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        User user = AuthUtil.getCurrentUser();
        return notificationRepository.countByUserIdAndIsReadFalse(user.getId());
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        User user = AuthUtil.getCurrentUser();
        int updated = notificationRepository.markAsRead(notificationId, user.getId());
        if (updated == 0) {
            throw TaskHubException.notFound("Notification not found");
        }
    }

    @Transactional
    public void markAllAsRead() {
        User user = AuthUtil.getCurrentUser();
        notificationRepository.markAllAsRead(user.getId());
    }

    public NotificationResponse notifyTaskAssigned(Long studentId, String taskTitle, Long taskId) {
        return notify(studentId, NotificationType.TASK_ASSIGNED,
                "Bạn đã được nhận công việc",
                "Bạn đã được nhận cho công việc: " + taskTitle,
                "/student/tasks/" + taskId, taskId);
    }

    public NotificationResponse notifyApplicationReceived(Long hirerId, String studentName, String taskTitle, Long taskId) {
        return notify(hirerId, NotificationType.TASK_APPLICATION_RECEIVED,
                "Đơn ứng tuyển mới",
                studentName + " đã ứng tuyển công việc: " + taskTitle,
                "/hirer/tasks/" + taskId, taskId);
    }

    public NotificationResponse notifyApplicationAccepted(Long studentId, String taskTitle, Long taskId) {
        return notifyAndEmail(studentId, NotificationType.TASK_APPLICATION_ACCEPTED,
                "Đơn ứng tuyển được chấp nhận!",
                "Bạn đã được nhận cho công việc: " + taskTitle,
                "/student/tasks/" + taskId, taskId, taskTitle);
    }

    public NotificationResponse notifySubmissionReceived(Long hirerId, String taskTitle, Long taskId) {
        return notify(hirerId, NotificationType.TASK_SUBMITTED,
                "Bài nộp mới",
                "Sinh viên đã nộp bài cho: " + taskTitle,
                "/hirer/submissions", taskId);
    }

    public NotificationResponse notifyRevisionRequested(Long studentId, String taskTitle, Long taskId) {
        return notify(studentId, NotificationType.TASK_REVISION_REQUESTED,
                "Yêu cầu chỉnh sửa",
                "Nhà tuyển dụng yêu cầu chỉnh sửa cho: " + taskTitle,
                "/student/tasks/" + taskId, taskId);
    }

    public NotificationResponse notifyTaskCompleted(Long studentId, String taskTitle, Long taskId) {
        return notify(studentId, NotificationType.TASK_COMPLETED,
                "Công việc hoàn thành",
                "Công việc '" + taskTitle + "' đã được xác nhận hoàn thành!",
                "/student/tasks/" + taskId, taskId);
    }

    public NotificationResponse notifyPaymentReleased(Long studentId, String taskTitle, Long taskId) {
        return notify(studentId, NotificationType.PAYMENT_RECEIVED,
                "Thanh toán đã được giải ngân",
                "Bạn đã nhận được thanh toán cho công việc: " + taskTitle,
                "/student/wallet", taskId);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .isRead(n.getIsRead())
                .readAt(n.getReadAt())
                .actionUrl(n.getActionUrl())
                .taskId(n.getTaskId())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
