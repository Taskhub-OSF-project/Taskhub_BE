package com.taskhub.service;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.request.SendMessageRequest;
import com.taskhub.dto.response.ConversationResponse;
import com.taskhub.dto.response.MessageResponse;
import com.taskhub.entity.*;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.*;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingService {
    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final TaskRepository taskRepository;
    private final TaskApplicationRepository taskApplicationRepository;
    private final WebSocketPushService pushService;

    @Transactional
    public ConversationResponse getOrCreateConversation(Long taskId) {
        return getOrCreateConversation(taskId, null);
    }

    @Transactional
    public ConversationResponse getOrCreateConversation(Long taskId, Long otherUserId) {
        User currentUser = AuthUtil.getCurrentUser();
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> TaskHubException.notFound("Không tìm thấy công việc"));

        boolean isHirer = task.getHirer().getId().equals(currentUser.getId());
        boolean isAssignedStudent = task.getAssignedTo() != null
                && task.getAssignedTo().getId().equals(currentUser.getId());
        boolean isApplicant = taskApplicationRepository.existsByTaskIdAndStudentId(taskId, currentUser.getId());

        User hirer = task.getHirer();
        User student;
        if (isHirer) {
            student = resolveStudentForHirer(task, otherUserId);
        } else if (isAssignedStudent || isApplicant) {
            student = currentUser;
        } else {
            throw TaskHubException.forbidden("Bạn cần ứng tuyển hoặc được giao công việc này trước khi nhắn tin với người thuê");
        }
        if (student == null) {
            throw TaskHubException.badRequest("Chưa chọn sinh viên để mở hội thoại");
        }

        Conversation conv = conversationRepo.findByTaskAndParticipants(taskId, hirer.getId(), student.getId())
                .orElseGet(() -> {
                    Conversation newConv = Conversation.builder()
                            .task(task)
                            .participantA(hirer)
                            .participantB(student)
                            .build();
                    return conversationRepo.save(newConv);
                });

        return toConversationResponse(conv, currentUser.getId());
    }

    private User resolveStudentForHirer(Task task, Long otherUserId) {
        if (otherUserId == null) {
            return task.getAssignedTo();
        }
        if (task.getAssignedTo() != null && task.getAssignedTo().getId().equals(otherUserId)) {
            return task.getAssignedTo();
        }
        return taskApplicationRepository.findByTaskIdAndStudentId(task.getId(), otherUserId)
                .map(TaskApplication::getStudent)
                .orElseThrow(() -> TaskHubException.forbidden("Sinh viên này chưa ứng tuyển công việc"));
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> getMyConversations(PageRequestDto pageReq) {
        User user = AuthUtil.getCurrentUser();
        Page<Conversation> page = conversationRepo.findByParticipantId(user.getId(),
                org.springframework.data.domain.PageRequest.of(
                        pageReq.getPage(),
                        Math.min(pageReq.getSize(), 50),
                        Sort.by(Sort.Direction.DESC, "lastMessageAt")));
        return PageResponse.<ConversationResponse>builder()
                .content(page.getContent().stream()
                        .filter(this::hasTwoParticipants)
                        .map(c -> toConversationResponse(c, user.getId())).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversationsList() {
        User user = AuthUtil.getCurrentUser();
        return conversationRepo.findByParticipantIdOrderByLastMessage(user.getId()).stream()
                .filter(this::hasTwoParticipants)
                .map(c -> toConversationResponse(c, user.getId())).toList();
    }

    @Transactional
    public MessageResponse sendMessage(Long conversationId, SendMessageRequest req) {
        User sender = AuthUtil.getCurrentUser();

        Conversation conv = conversationRepo.findByIdAndParticipantId(conversationId, sender.getId())
                .orElseThrow(() -> TaskHubException.notFound("Conversation not found"));

        Message message = Message.builder()
                .conversation(conv)
                .sender(sender)
                .content(req.getContent().trim())
                .build();
        message = messageRepo.save(message);

        conv.setLastMessageAt(LocalDateTime.now());
        conv.setLastMessagePreview(truncate(req.getContent(), 100));
        boolean senderIsA = sender.getId().equals(conv.getParticipantA().getId());
        conv.incrementUnread(!senderIsA);
        conversationRepo.save(conv);

        MessageResponse response = toMessageResponse(message);

        Long recipientId = senderIsA ? conv.getParticipantB().getId() : conv.getParticipantA().getId();
        pushService.pushToUser(recipientId, "NEW_MESSAGE", response);

        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> getMessages(Long conversationId, PageRequestDto pageReq) {
        User currentUser = AuthUtil.getCurrentUser();
        conversationRepo.findByIdAndParticipantId(conversationId, currentUser.getId())
                .orElseThrow(() -> TaskHubException.notFound("Conversation not found"));

        Page<Message> page = messageRepo.findByConversationId(conversationId,
                org.springframework.data.domain.PageRequest.of(
                        pageReq.getPage(),
                        Math.min(pageReq.getSize(), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return PageResponse.<MessageResponse>builder()
                .content(page.getContent().stream().map(this::toMessageResponse).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional
    public void markAsRead(Long conversationId) {
        User currentUser = AuthUtil.getCurrentUser();
        Conversation conv = conversationRepo.findByIdAndParticipantId(conversationId, currentUser.getId())
                .orElseThrow(() -> TaskHubException.notFound("Conversation not found"));
        boolean isA = currentUser.getId().equals(conv.getParticipantA().getId());
        conv.resetUnread(isA);
        conversationRepo.save(conv);
        messageRepo.markAllAsRead(conversationId, currentUser.getId());
    }

    @Transactional(readOnly = true)
    public long getTotalUnreadCount() {
        User user = AuthUtil.getCurrentUser();
        return messageRepo.countUnreadForUser(user.getId());
    }

    private ConversationResponse toConversationResponse(Conversation c, Long currentUserId) {
        Long otherId = c.getParticipantA().getId().equals(currentUserId)
                ? c.getParticipantB().getId() : c.getParticipantA().getId();
        String otherName = c.getParticipantA().getId().equals(currentUserId)
                ? c.getParticipantB().getFullName() : c.getParticipantA().getFullName();

        return ConversationResponse.builder()
                .id(c.getId())
                .taskId(c.getTask().getId())
                .taskTitle(c.getTask().getTitle())
                .participantAId(c.getParticipantA().getId())
                .participantAName(c.getParticipantA().getFullName())
                .participantBId(c.getParticipantB().getId())
                .participantBName(c.getParticipantB().getFullName())
                .otherUserId(otherId)
                .otherUserName(otherName)
                .lastMessagePreview(c.getLastMessagePreview())
                .lastMessageAt(c.getLastMessageAt())
                .unreadCount(c.getUnreadCountFor(currentUserId))
                .createdAt(c.getCreatedAt())
                .build();
    }

    private boolean hasTwoParticipants(Conversation c) {
        return c.getParticipantA() != null
                && c.getParticipantB() != null
                && !c.getParticipantA().getId().equals(c.getParticipantB().getId());
    }

    private MessageResponse toMessageResponse(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversation().getId())
                .senderId(m.getSender().getId())
                .senderName(m.getSender().getFullName())
                .content(m.getContent())
                .isRead(m.getIsRead())
                .readAt(m.getReadAt())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
