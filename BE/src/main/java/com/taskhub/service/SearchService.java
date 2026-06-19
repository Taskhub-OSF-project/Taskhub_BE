package com.taskhub.service;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.response.FreelancerSearchResponse;
import com.taskhub.dto.response.PublicTaskResponse;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.repository.ReviewRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public PageResponse<FreelancerSearchResponse> searchFreelancers(String keyword, PageRequestDto pageReq) {
        Page<User> page;
        org.springframework.data.domain.Pageable springPage = org.springframework.data.domain.PageRequest.of(
                pageReq.getPage(), Math.min(pageReq.getSize(), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        if (keyword != null && !keyword.isBlank()) {
            page = userRepository.searchByKeyword(Role.STUDENT, keyword.trim(), springPage);
        } else {
            page = userRepository.findByRole(Role.STUDENT, springPage);
        }

        return PageResponse.<FreelancerSearchResponse>builder()
                .content(page.getContent().stream().map(this::toFreelancerResponse).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicTaskResponse> searchTasks(String keyword, String category, String status, PageRequestDto pageReq) {
        org.springframework.data.domain.Pageable springPage = org.springframework.data.domain.PageRequest.of(
                pageReq.getPage(), Math.min(pageReq.getSize(), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Task> page = taskRepository.findByStatusIn(List.of(TaskStatus.ACTIVE), springPage);

        List<PublicTaskResponse> results = page.getContent().stream()
                .filter(t -> keyword == null || keyword.isBlank() ||
                        containsKeyword(t, keyword))
                .filter(t -> category == null || category.isBlank() ||
                        (t.getCategory() != null && t.getCategory().equalsIgnoreCase(category)))
                .map(this::toPublicTaskResponse)
                .toList();

        return PageResponse.<PublicTaskResponse>builder()
                .content(results)
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
                "Kế toán", "Video / Animation", "Kinh doanh", "Gia sư"
        );
    }

    private FreelancerSearchResponse toFreelancerResponse(User u) {
        Double avgRating = reviewRepository.getAverageRating(u.getId(),
                com.taskhub.enums.ReviewType.FREELANCER_TO_HIRER);
        long totalReviews = reviewRepository.countByRevieweeIdAndType(u.getId(),
                com.taskhub.enums.ReviewType.FREELANCER_TO_HIRER);
        long completedTasks = taskRepository.findByAssignedToIdAndStatus(u.getId(),
                TaskStatus.COMPLETED).size();

        return FreelancerSearchResponse.builder()
                .id(u.getId())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .university(u.getUniversity())
                .major(u.getMajor())
                .bio(u.getBio())
                .skills(u.getSkills())
                .experience(u.getExperience())
                .portfolioUrl(u.getPortfolioUrl())
                .avatarUrl(u.getAvatarUrl())
                .availability(u.getAvailability())
                .languages(u.getLanguages())
                .certifications(u.getCertifications())
                .averageRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : null)
                .totalReviews(totalReviews)
                .completedTasks(completedTasks)
                .memberSince(u.getCreatedAt() != null ? u.getCreatedAt().toString() : null)
                .build();
    }

    private PublicTaskResponse toPublicTaskResponse(Task t) {
        return PublicTaskResponse.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(truncate(t.getDescription(), 200))
                .category(t.getCategory())
                .budget(formatBudget(t.getBudget()))
                .deadline(t.getDeadline() != null ? t.getDeadline().toString() : null)
                .status(t.getStatus().name())
                .hirerName(t.getHirer().getFullName())
                .hirerId(t.getHirer().getId())
                .hirerAvatarUrl(t.getHirer().getAvatarUrl())
                .skillsRequired(t.getSkillsRequired())
                .applicantCount(t.getApplicantCount())
                .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().toString() : null)
                .build();
    }

    private boolean containsKeyword(Task t, String keyword) {
        String k = keyword.toLowerCase();
        return (t.getTitle() != null && t.getTitle().toLowerCase().contains(k))
                || (t.getDescription() != null && t.getDescription().toLowerCase().contains(k))
                || (t.getCategory() != null && t.getCategory().toLowerCase().contains(k))
                || (t.getSkillsRequired() != null && t.getSkillsRequired().stream()
                        .anyMatch(s -> s.toLowerCase().contains(k)));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private String formatBudget(BigDecimal budget) {
        if (budget == null) return null;
        return String.format("%,.0f", budget.doubleValue());
    }
}
