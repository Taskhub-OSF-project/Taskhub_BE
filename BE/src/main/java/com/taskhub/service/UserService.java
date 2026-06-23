package com.taskhub.service;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.request.UserProfileUpdateRequest;
import com.taskhub.dto.response.UserProfileResponse;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.ReviewType;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.ReviewRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.security.AuthUtil;
import com.taskhub.dto.response.AdminDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile() {
        User current = AuthUtil.getCurrentUser();
        return buildProfile(current.getId());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        userRepository.findById(userId).orElseThrow(() -> TaskHubException.notFound("User not found"));
        return buildProfile(userId);
    }

    @Transactional
    public UserProfileResponse updateMyProfile(UserProfileUpdateRequest req) {
        User current = AuthUtil.getCurrentUser();
        User user = userRepository.findById(current.getId())
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        if (req.getFullName() != null && !req.getFullName().isBlank()) {
            user.setFullName(req.getFullName().trim());
        }
        // Support both 'school' (FE convention) and 'university' field
        String schoolValue = req.getSchool() != null ? req.getSchool() : req.getUniversity();
        user.setUniversity(trimToNull(schoolValue));
        user.setMajor(trimToNull(req.getMajor()));
        user.setBio(trimToNull(req.getBio()));
        user.setExperience(trimToNull(req.getExperience()));
        user.setPortfolioUrl(trimToNull(req.getPortfolioUrl()));
        user.setPhone(trimToNull(req.getPhone()));
        user.setTitle(trimToNull(req.getTitle()));
        user.setHourlyRate(trimToNull(req.getHourlyRate()));
        user.setAvailability(trimToNull(req.getAvailability()));
        user.setAvatarUrl(trimToNull(req.getAvatarUrl()));
        if (req.getSkills() != null) user.setSkills(req.getSkills());
        if (req.getLanguages() != null) user.setLanguages(req.getLanguages());
        if (req.getCertifications() != null) user.setCertifications(req.getCertifications());
        if (req.getDateOfBirth() != null) user.setDateOfBirth(req.getDateOfBirth());

        user = userRepository.save(user);
        return buildProfile(user.getId());
    }

    @Transactional
    public void setAvailability(boolean available) {
        User current = AuthUtil.getCurrentUser();
        User user = userRepository.findById(current.getId())
                .orElseThrow(() -> TaskHubException.notFound("User not found"));
        user.setIsAvailable(available);
        userRepository.save(user);
    }

    @Transactional
    public UserProfileResponse changeUserRole(Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));
        user.setRole(newRole);
        user = userRepository.save(user);
        log.info("Admin changed role of user {} to {}", userId, newRole);
        return buildProfile(user.getId());
    }

    @Transactional
    public void setUserBanned(Long userId, boolean banned) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));
        user.setIsBanned(banned);
        userRepository.save(user);
        log.info("Admin {} user {}", banned ? "banned" : "unbanned", userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> getAllUsers(PageRequestDto pageReq) {
        var springPage = org.springframework.data.domain.PageRequest.of(
                pageReq.getPage(), Math.min(pageReq.getSize(), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> page = userRepository.findAll(springPage);
        return PageResponse.<UserProfileResponse>builder()
                .content(page.getContent().stream().map(u -> buildProfile(u.getId())).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> getUsersByRole(Role role, PageRequestDto pageReq) {
        var springPage = org.springframework.data.domain.PageRequest.of(
                pageReq.getPage(), Math.min(pageReq.getSize(), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> page = userRepository.findByRole(role, springPage);
        return PageResponse.<UserProfileResponse>builder()
                .content(page.getContent().stream().map(u -> buildProfile(u.getId())).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getAdminDashboardStats() {
        long totalUsers = userRepository.count();
        long totalStudents = userRepository.countByRole(Role.STUDENT);
        long totalHirers = userRepository.countByRole(Role.HIRER);
        long totalTasks = taskRepository.count();
        List<Task> completedTaskList = taskRepository.findByStatusIn(List.of(TaskStatus.COMPLETED));
        long activeTasks = taskRepository.findByStatusIn(List.of(TaskStatus.ACTIVE)).size();
        long disputedTasks = taskRepository.findByStatusIn(List.of(TaskStatus.DISPUTED)).size();
        BigDecimal totalVolume = completedTaskList.stream().map(Task::getBudget).reduce(BigDecimal.ZERO, BigDecimal::add);

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .totalStudents(totalStudents)
                .totalHirers(totalHirers)
                .totalTasks(totalTasks)
                .activeTasks(activeTasks)
                .completedTasks(completedTaskList.size())
                .disputedTasks(disputedTasks)
                .totalPlatformVolume(totalVolume)
                .build();
    }

    private UserProfileResponse buildProfile(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return null;

        Double avgFreelancer = reviewRepository.getAverageRating(userId, ReviewType.FREELANCER_TO_HIRER);
        Double avgHirer = reviewRepository.getAverageRating(userId, ReviewType.HIRER_TO_FREELANCER);
        long reviewsFreelancer = reviewRepository.countByRevieweeIdAndType(userId, ReviewType.FREELANCER_TO_HIRER);
        long reviewsHirer = reviewRepository.countByRevieweeIdAndType(userId, ReviewType.HIRER_TO_FREELANCER);

        BigDecimal totalEarnings = taskRepository.findByAssignedToIdAndStatus(userId, TaskStatus.COMPLETED).stream()
                .map(Task::getBudget).reduce(BigDecimal.ZERO, BigDecimal::add);
        long completedFreelancer = taskRepository.findByAssignedToIdAndStatus(userId, TaskStatus.COMPLETED).size();
        long completedHirer = taskRepository.findByHirerIdAndStatus(userId, TaskStatus.COMPLETED).size();

        return UserProfileResponse.builder()
                .id(user.getId()).email(user.getEmail()).fullName(user.getFullName())
                .university(user.getUniversity()).major(user.getMajor())
                .bio(user.getBio()).skills(user.getSkills()).experience(user.getExperience())
                .portfolioUrl(user.getPortfolioUrl()).phone(user.getPhone())
                .title(user.getTitle()).hourlyRate(user.getHourlyRate())
                .availability(user.getAvailability()).languages(user.getLanguages())
                .certifications(user.getCertifications()).avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name()).walletBalance(user.getWalletBalance())
                .isVerified(user.getIsVerified()).isAvailable(user.getIsAvailable())
                .isBanned(user.getIsBanned())
                .dateOfBirth(user.getDateOfBirth())
                .age(user.getDateOfBirth() != null
                        ? Period.between(user.getDateOfBirth(), LocalDate.now()).getYears()
                        : null)
                .averageRatingAsFreelancer(avgFreelancer != null ? Math.round(avgFreelancer * 10.0) / 10.0 : null)
                .averageRatingAsHirer(avgHirer != null ? Math.round(avgHirer * 10.0) / 10.0 : null)
                .totalReviewsAsFreelancer(reviewsFreelancer).totalReviewsAsHirer(reviewsHirer)
                .totalEarnings(totalEarnings)
                .completedTasksAsFreelancer(completedFreelancer).completedTasksAsHirer(completedHirer)
                .memberSince(user.getCreatedAt() != null ? user.getCreatedAt().toLocalDate().toString() : null)
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}