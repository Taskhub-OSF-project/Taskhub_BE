package com.taskhub.service;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.request.BroadcastNotificationRequest;
import com.taskhub.dto.response.NotificationResponse;
import com.taskhub.entity.Notification;
import com.taskhub.entity.User;
import com.taskhub.enums.NotificationType;
import com.taskhub.enums.Role;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.NotificationRepository;
import com.taskhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void notify(Long userId, NotificationType type, String title, String body, String link, Long relatedId) {
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .link(link)
                .relatedId(relatedId)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void notifyApplicationReceived(Long hirerId, String studentName, String taskTitle, Long taskId) {
        notify(hirerId, NotificationType.TASK_APPLICATION_RECEIVED,
                "Ứng viên mới cho công việc của bạn",
                studentName + " vừa ứng tuyển vào công việc \"" + taskTitle + "\".",
                "/tasks/" + taskId, taskId);
    }

    @Transactional
    public void notifyTaskAssigned(Long studentId, String taskTitle, Long taskId) {
        notify(studentId, NotificationType.TASK_ASSIGNED,
                "Bạn đã được nhận công việc",
                "Bạn đã được nhận công việc: \"" + taskTitle + "\".",
                "/tasks/" + taskId, taskId);
    }

    @Transactional
    public void notifyApplicationAccepted(Long studentId, String taskTitle, Long taskId) {
        notify(studentId, NotificationType.TASK_APPLICATION_ACCEPTED,
                "Đơn ứng tuyển được chấp nhận",
                "Đơn ứng tuyển của bạn cho \"" + taskTitle + "\" đã được chấp nhận.",
                "/tasks/" + taskId, taskId);
    }

    public PageResponse<NotificationResponse> listForUser(Long userId, PageRequestDto req) {
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(req.getPage(), Math.min(req.getSize(), 100)));
        List<NotificationResponse> items = page.getContent().stream()
                .map(NotificationResponse::from)
                .toList();
        return PageResponse.of(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    public List<NotificationResponse> unreadForUser(Long userId) {
        return notificationRepository.findTop20ByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void notifyRevisionRequested(Long studentId, String taskTitle, Long taskId) {
        notifyRevisionRequested(studentId, taskTitle, taskId, null, null);
    }

    @Transactional
    public void notifyRevisionRequested(Long studentId, String taskTitle, Long taskId, String reason, String description) {
        StringBuilder message = new StringBuilder("Revision requested for: ").append(taskTitle);
        if (reason != null && !reason.isBlank()) {
            message.append(". Reason: ").append(reason.trim());
        }
        if (description != null && !description.isBlank()) {
            message.append(". Note: ").append(description.trim());
        }
        notify(studentId, NotificationType.TASK_REVISION_REQUESTED,
                "Revision requested",
                message.toString(),
                "/student/tasks/" + taskId, taskId);
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        int updated = notificationRepository.markRead(notificationId, userId, LocalDateTime.now());
        if (updated == 0) {
            throw new TaskHubException("Notification not found", HttpStatus.NOT_FOUND);
        }
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId, LocalDateTime.now());
    }

    @Transactional
    public int broadcast(BroadcastNotificationRequest req) {
        Role targetRole = null;
        if (req.getTargetRole() != null && !req.getTargetRole().isBlank()) {
            try {
                targetRole = Role.valueOf(req.getTargetRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new TaskHubException("Invalid targetRole: " + req.getTargetRole(), HttpStatus.BAD_REQUEST);
            }
        }

        List<User> recipients = (targetRole == null)
                ? userRepository.findAll().stream()
                        .filter(u -> u.getRole() != Role.ADMIN)
                        .toList()
                : userRepository.findByRole(targetRole, PageRequest.of(0, 10_000))
                        .getContent().stream()
                        .filter(u -> u.getRole() != Role.ADMIN)
                        .toList();

        NotificationType type = req.getType() != null ? req.getType() : NotificationType.SYSTEM_ANNOUNCEMENT;
        LocalDateTime now = LocalDateTime.now();
        List<Notification> batch = new ArrayList<>(recipients.size());
        for (User u : recipients) {
            batch.add(Notification.builder()
                    .userId(u.getId())
                    .type(type)
                    .title(req.getTitle())
                    .body(req.getBody())
                    .link(req.getLink())
                    .relatedId(req.getRelatedId())
                    .isRead(false)
                    .createdAt(now)
                    .build());
        }
        notificationRepository.saveAll(batch);
        log.info("Broadcast sent to {} users (targetRole={})", batch.size(),
                req.getTargetRole() == null ? "ALL" : req.getTargetRole());
        return batch.size();
    }
}
