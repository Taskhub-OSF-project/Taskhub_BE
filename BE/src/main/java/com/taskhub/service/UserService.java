package com.taskhub.service;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.request.ChangePasswordRequest;
import com.taskhub.dto.request.UpdateProfileRequest;
import com.taskhub.dto.response.AdminDashboardResponse;
import com.taskhub.dto.response.AuthResponse;
import com.taskhub.dto.response.UserProfileResponse;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.ReviewType;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.RefreshTokenRepository;
import com.taskhub.repository.ReviewRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final com.taskhub.security.JwtService jwtService;

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));
        return buildProfile(userId);
    }

    /**
     * Đổi role của user (HIRER ↔ STUDENT) và trả về AuthResponse chứa
     * JWT mới mang role vừa đổi để FE cập nhật token.
     */
    @Transactional
    public AuthResponse switchRoleAndReturnToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        Role current = user.getRole();
        if (current == Role.ADMIN) {
            throw TaskHubException.badRequest("Admin account cannot switch roles");
        }
        Role target = (current == Role.HIRER) ? Role.STUDENT : Role.HIRER;
        user.setRole(target);
        user = userRepository.save(user);
        auditService.record("ROLE_SWITCH", user.getEmail(), "Switched from " + current + " to " + target);

        String newToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), target.name());
        UserProfileResponse profile = buildProfile(user.getId());
        return AuthResponse.builder()
                .token(newToken)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(target)
                .build();
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        if (req.getFullName() != null) {
            String name = req.getFullName().trim();
            if (name.isBlank()) throw TaskHubException.badRequest("Full name cannot be blank");
            user.setFullName(name);
        }
        if (req.getUniversity() != null) {
            user.setUniversity(trimToNull(req.getUniversity()));
        }
        if (req.getMajor() != null) {
            user.setMajor(trimToNull(req.getMajor()));
        }
        if (req.getBio() != null) {
            user.setBio(trimToNull(req.getBio()));
        }
        if (req.getSkills() != null) {
            user.setSkills(cleanList(req.getSkills()));
        }
        if (req.getExperience() != null) {
            user.setExperience(trimToNull(req.getExperience()));
        }
        if (req.getPortfolioUrl() != null) {
            user.setPortfolioUrl(trimToNull(req.getPortfolioUrl()));
        }
        if (req.getPhone() != null) {
            user.setPhone(trimToNull(req.getPhone()));
        }
        if (req.getTitle() != null) {
            user.setTitle(trimToNull(req.getTitle()));
        }
        if (req.getHourlyRate() != null) {
            user.setHourlyRate(trimToNull(req.getHourlyRate()));
        }
        if (req.getAvailability() != null) {
            user.setAvailability(trimToNull(req.getAvailability()));
        }
        if (req.getLanguages() != null) {
            user.setLanguages(cleanList(req.getLanguages()));
        }
        if (req.getCertifications() != null) {
            user.setCertifications(cleanList(req.getCertifications()));
        }
        if (req.getAvatarUrl() != null) {
            user.setAvatarUrl(trimToNull(req.getAvatarUrl()));
        }

        userRepository.save(user);
        auditService.record("PROFILE_UPDATE", user.getEmail(), "Profile updated");
        return buildProfile(userId);
    }

    @Transactional
    public UserProfileResponse setAvailability(Long userId, boolean available) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));
        user.setIsAvailable(available);
        user.setAvailability(available ? "Sẵn sàng" : "Không sẵn sàng");
        userRepository.save(user);
        auditService.record("PROFILE_UPDATE", user.getEmail(), "Availability updated");
        return buildProfile(userId);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            auditService.record("PASSWORD_CHANGE_FAILURE", user.getEmail(), "Current password incorrect");
            throw TaskHubException.badRequest("Current password is incorrect");
        }

        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw TaskHubException.badRequest("New password must differ from current password");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(userId);

        auditService.record("PASSWORD_CHANGE_SUCCESS", user.getEmail(), "Password changed successfully");
    }

    // ── Admin Methods ────────────────────────────────────────────

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

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> getUsersByRole(Role role, PageRequestDto pageReq) {
        var springPage = PageRequest.of(
                pageReq.getPage(), Math.min(pageReq.getSize(), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> page = userRepository.findByRole(role, springPage);
        return toPageResponse(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> getAllUsers(PageRequestDto pageReq) {
        var springPage = PageRequest.of(
                pageReq.getPage(), Math.min(pageReq.getSize(), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> page = userRepository.findAll(springPage);
        return toPageResponse(page);
    }

    @Transactional
    public UserProfileResponse switchRole(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        Role current = user.getRole();
        if (current == Role.ADMIN) {
            throw TaskHubException.badRequest("Admin account cannot switch roles");
        }
        Role target = (current == Role.HIRER) ? Role.STUDENT : Role.HIRER;
        user.setRole(target);
        user = userRepository.save(user);
        auditService.record("ROLE_SWITCH", user.getEmail(), "Switched from " + current + " to " + target);
        return buildProfile(user.getId());
    }

    @Transactional
    public UserProfileResponse changeUserRole(Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));
        user.setRole(newRole);
        user = userRepository.save(user);
        return buildProfile(user.getId());
    }

    @Transactional
    public void setUserBanned(Long userId, boolean banned) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));
        user.setIsBanned(banned);
        userRepository.save(user);
    }

    // ── Helpers ─────────────────────────────────────────────────

    private PageResponse<UserProfileResponse> toPageResponse(Page<User> page) {
        return PageResponse.<UserProfileResponse>builder()
                .content(page.getContent().stream().map(u -> buildProfile(u.getId())).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
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
                .role(user.getRole().name()).roleEnum(user.getRole())
                .walletBalance(user.getWalletBalance())
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
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) return null;
        return values.stream()
                .map(this::trimToNull)
                .filter(v -> v != null)
                .toList();
    }
}
