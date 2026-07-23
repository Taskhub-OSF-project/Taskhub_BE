package com.taskhub.service;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.response.FreelancerSearchResponse;
import com.taskhub.dto.response.PublicTaskResponse;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.ReviewType;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.repository.ReviewRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public PageResponse<FreelancerSearchResponse> searchFreelancers(String keyword, PageRequestDto pageReq) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                pageReq.getPage(), Math.min(pageReq.getSize(), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedKeyword = normalizeFilter(keyword);
        Page<User> page = normalizedKeyword == null
                ? userRepository.findByRole(Role.STUDENT, pageable)
                : userRepository.searchByKeyword(Role.STUDENT, normalizedKeyword, pageable);

        List<Long> userIds = page.getContent().stream().map(User::getId).toList();
        Map<Long, ReviewStats> reviewStats = loadReviewStats(userIds);
        Map<Long, TaskStats> taskStats = loadTaskStats(userIds);

        return PageResponse.<FreelancerSearchResponse>builder()
                .content(page.getContent().stream()
                        .map(user -> toFreelancerResponse(user,
                                reviewStats.get(user.getId()), taskStats.get(user.getId())))
                        .toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicTaskResponse> searchTasks(
            String keyword, String category, String ignoredStatus, PageRequestDto pageReq) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                pageReq.getPage(), Math.min(pageReq.getSize(), 50));

        Page<Task> page = taskRepository.searchPublicTasks(
                TaskStatus.ACTIVE.name(), normalizeFilter(keyword), normalizeFilter(category),
                LocalDateTime.now(), pageable);

        return PageResponse.<PublicTaskResponse>builder()
                .content(page.getContent().stream().map(this::toPublicTaskResponse).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public List<String> getPopularCategories() {
        return List.of(
                "Thiết kế đồ họa", "Lập trình Web", "Lập trình Mobile",
                "Viết bài / Content", "Dịch thuật", "Marketing",
                "Kế toán", "Video / Animation", "Kinh doanh", "Gia sư");
    }

    private FreelancerSearchResponse toFreelancerResponse(User user, ReviewStats reviews, TaskStats tasks) {
        return FreelancerSearchResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .university(user.getUniversity())
                .major(user.getMajor())
                .bio(user.getBio())
                .skills(user.getSkills())
                .experience(user.getExperience())
                .portfolioUrl(user.getPortfolioUrl())
                .avatarUrl(user.getAvatarUrl())
                .availability(user.getAvailability())
                .languages(user.getLanguages())
                .certifications(user.getCertifications())
                .averageRating(reviews == null ? null : Math.round(reviews.average() * 10.0) / 10.0)
                .totalReviews(reviews == null ? 0L : reviews.count())
                .completedTasks(tasks == null ? 0L : tasks.count())
                .memberSince(user.getCreatedAt() == null ? null : user.getCreatedAt().toString())
                .build();
    }

    private PublicTaskResponse toPublicTaskResponse(Task task) {
        return PublicTaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(truncate(task.getDescription(), 200))
                .category(task.getCategory())
                .budget(formatBudget(task.getBudget()))
                .deadline(task.getDeadline() == null ? null : task.getDeadline().toString())
                .status(task.getStatus().name())
                .hirerName(task.getHirer().getFullName())
                .hirerId(task.getHirer().getId())
                .hirerAvatarUrl(task.getHirer().getAvatarUrl())
                .skillsRequired(task.getSkillsRequired())
                .applicantCount(task.getApplicantCount())
                .createdAt(task.getCreatedAt() == null ? null : task.getCreatedAt().toString())
                .build();
    }

    private Map<Long, ReviewStats> loadReviewStats(List<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<Long, ReviewStats> result = new HashMap<>();
        for (Object[] row : reviewRepository.getPublicStatsForUsers(
                userIds, ReviewType.HIRER_TO_FREELANCER)) {
            result.put(((Number) row[0]).longValue(),
                    new ReviewStats(((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()));
        }
        return result;
    }

    private Map<Long, TaskStats> loadTaskStats(List<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<Long, TaskStats> result = new HashMap<>();
        for (Object[] row : taskRepository.getAssigneeTaskStats(userIds, TaskStatus.COMPLETED)) {
            result.put(((Number) row[0]).longValue(),
                    new TaskStats(((Number) row[1]).longValue(), (BigDecimal) row[2]));
        }
        return result;
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), 200));
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private String formatBudget(BigDecimal budget) {
        return budget == null ? null : String.format("%,.0f", budget.doubleValue());
    }

    private record ReviewStats(double average, long count) {}
    private record TaskStats(long count, BigDecimal totalBudget) {}
}
