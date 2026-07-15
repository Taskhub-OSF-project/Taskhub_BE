package com.taskhub.service;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.request.CreateReviewRequest;
import com.taskhub.dto.response.ReviewResponse;
import com.taskhub.dto.response.UserProfileResponse;
import com.taskhub.entity.*;
import com.taskhub.enums.*;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.*;
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
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Transactional
    public ReviewResponse createReview(Long taskId, CreateReviewRequest req) {
        User currentUser = AuthUtil.getCurrentUser();
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> TaskHubException.notFound("Task not found"));

        if (task.getStatus() != TaskStatus.COMPLETED) {
            throw TaskHubException.badRequest("Can only review completed tasks");
        }

        boolean isHirer = task.getHirer().getId().equals(currentUser.getId());
        boolean isFreelancer = task.getAssignedTo() != null
                && task.getAssignedTo().getId().equals(currentUser.getId());

        if (!isHirer && !isFreelancer) {
            throw TaskHubException.forbidden("Only task participants can review");
        }

        ReviewType reviewType = isHirer ? ReviewType.HIRER_TO_FREELANCER : ReviewType.FREELANCER_TO_HIRER;
        User reviewee = isHirer ? task.getAssignedTo() : task.getHirer();

        if (reviewRepository.existsByTaskIdAndReviewerIdAndType(taskId, currentUser.getId(), reviewType)) {
            throw TaskHubException.badRequest("You have already reviewed this task");
        }

        Review review = Review.builder()
                .task(task)
                .reviewer(currentUser)
                .reviewee(reviewee)
                .type(reviewType)
                .rating(req.getRating())
                .comment(req.getComment())
                .isPublic(true)
                .build();
        review = reviewRepository.save(review);

        String title = isHirer ? "Đánh giá mới từ sinh viên" : "Đánh giá từ người thuê";
        StringBuilder message = new StringBuilder(currentUser.getFullName())
                .append(" đã đánh giá bạn ")
                .append(req.getRating())
                .append("/5 sao cho công việc: ")
                .append(task.getTitle());
        if (req.getComment() != null && !req.getComment().isBlank()) {
            message.append(". Nhận xét: ").append(req.getComment().trim());
        }
        notificationService.notify(
                reviewee.getId(),
                NotificationType.REVIEW_RECEIVED,
                title,
                message.toString(),
                "/profile/" + currentUser.getId(),
                taskId
        );

        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getReviewsForUser(Long userId, PageRequestDto pageReq) {
        Page<Review> page = reviewRepository.findByRevieweeIdAndIsPublicTrueOrderByCreatedAtDesc(
                userId,
                org.springframework.data.domain.PageRequest.of(
                        pageReq.getPage(),
                        Math.min(pageReq.getSize(), 50),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.<ReviewResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getLatestPublicReviews(PageRequestDto pageReq) {
        Page<Review> page = reviewRepository.findByIsPublicTrueOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(
                        pageReq.getPage(),
                        Math.min(pageReq.getSize(), 50)));
        return PageResponse.<ReviewResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        Double avgFreelancer = reviewRepository.getAverageRating(userId, ReviewType.HIRER_TO_FREELANCER);
        Double avgHirer = reviewRepository.getAverageRating(userId, ReviewType.FREELANCER_TO_HIRER);
        long reviewsFreelancer = reviewRepository.countByRevieweeIdAndType(userId, ReviewType.HIRER_TO_FREELANCER);
        long reviewsHirer = reviewRepository.countByRevieweeIdAndType(userId, ReviewType.FREELANCER_TO_HIRER);

        long completedFreelancer = taskRepository.findByAssignedToIdAndStatus(userId, TaskStatus.COMPLETED).size();
        long completedHirer = taskRepository.findByHirerIdAndStatus(userId, TaskStatus.COMPLETED).size();

        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .university(user.getUniversity())
                .major(user.getMajor())
                .bio(user.getBio())
                .skills(user.getSkills())
                .experience(user.getExperience())
                .portfolioUrl(user.getPortfolioUrl())
                .title(user.getTitle())
                .hourlyRate(user.getHourlyRate())
                .availability(user.getAvailability())
                .languages(user.getLanguages())
                .certifications(user.getCertifications())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name())
                .roleEnum(user.getRole())
                .isVerified(user.getIsVerified())
                .isAvailable(user.getIsAvailable())
                .averageRatingAsFreelancer(avgFreelancer != null ? Math.round(avgFreelancer * 10.0) / 10.0 : null)
                .averageRatingAsHirer(avgHirer != null ? Math.round(avgHirer * 10.0) / 10.0 : null)
                .totalReviewsAsFreelancer(reviewsFreelancer)
                .totalReviewsAsHirer(reviewsHirer)
                .completedTasksAsFreelancer(completedFreelancer)
                .completedTasksAsHirer(completedHirer)
                .memberSince(user.getCreatedAt() != null ? user.getCreatedAt().toLocalDate().toString() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private ReviewResponse toResponse(Review r) {
        return ReviewResponse.builder()
                .id(r.getId())
                .taskId(r.getTask().getId())
                .taskTitle(r.getTask().getTitle())
                .reviewerId(r.getReviewer().getId())
                .reviewerName(r.getReviewer().getFullName())
                .revieweeId(r.getReviewee().getId())
                .revieweeName(r.getReviewee().getFullName())
                .type(r.getType())
                .rating(r.getRating())
                .comment(r.getComment())
                .isPublic(r.getIsPublic())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
