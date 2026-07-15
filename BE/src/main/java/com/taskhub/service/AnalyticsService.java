package com.taskhub.service;

import com.taskhub.dto.response.AnalyticsDashboardResponse;
import com.taskhub.dto.response.AnalyticsDashboardResponse.*;
import com.taskhub.entity.DailyMetric;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.ReviewType;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.*;
import com.taskhub.util.EscrowCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    private final DailyMetricRepository dailyMetricRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ReviewRepository reviewRepository;
    private final WalletTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public AnalyticsDashboardResponse getAnalyticsDashboard(int days) {
        if (days < 1 || days > 365) {
            throw TaskHubException.badRequest("days must be between 1 and 365");
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);
        List<Task> allTasks = taskRepository.findAll();
        List<User> allUsers = userRepository.findAll();

        PlatformOverview overview = buildOverview(allTasks);
        List<DailyMetricResponse> dailyMetrics = buildDailyMetrics(startDate, endDate);
        List<CategoryMetric> topCategories = buildCategoryMetrics(allTasks);
        List<UserGrowthMetric> userGrowth = buildUserGrowth(startDate, endDate, allUsers);
        TaskMetrics taskMetrics = buildTaskMetrics(allTasks);
        RevenueMetrics revenueMetrics = buildRevenueMetrics(allTasks);
        FreelancerMetrics freelancerMetrics = buildFreelancerMetrics(allUsers);

        return AnalyticsDashboardResponse.builder()
                .overview(overview)
                .dailyMetrics(dailyMetrics)
                .topCategories(topCategories)
                .userGrowth(userGrowth)
                .taskMetrics(taskMetrics)
                .revenueMetrics(revenueMetrics)
                .freelancerMetrics(freelancerMetrics)
                .build();
    }

    private PlatformOverview buildOverview(List<Task> allTasks) {
        long totalUsers = userRepository.count();
        long totalStudents = userRepository.countByRole(Role.STUDENT);
        long totalHirers = userRepository.countByRole(Role.HIRER);
        long totalTasks = taskRepository.count();

        long activeTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.ACTIVE || t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long completedTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        long disputedTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DISPUTED).count();

        BigDecimal totalVolume = allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .map(Task::getBudget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal platformRevenue = totalVolume.multiply(
                EscrowCalculator.getPlatformFeePercent().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        return PlatformOverview.builder()
                .totalUsers(totalUsers)
                .totalStudents(totalStudents)
                .totalHirers(totalHirers)
                .totalTasks(totalTasks)
                .activeTasks(activeTasks)
                .completedTasks(completedTasks)
                .disputedTasks(disputedTasks)
                .totalEscrowVolume(totalVolume)
                .totalPlatformRevenue(platformRevenue)
                .build();
    }

    private List<DailyMetricResponse> buildDailyMetrics(LocalDate start, LocalDate end) {
        return dailyMetricRepository.findByMetricDateBetweenOrderByMetricDateAsc(start, end)
                .stream()
                .map(this::toDailyMetricResponse)
                .collect(Collectors.toList());
    }

    private List<CategoryMetric> buildCategoryMetrics(List<Task> allTasks) {
        Map<String, List<Task>> byCategory = allTasks.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(Task::getCategory));

        return byCategory.entrySet().stream()
                .map(e -> {
                    String cat = e.getKey();
                    List<Task> catTasks = e.getValue();
                    int total = catTasks.size();
                    int completed = (int) catTasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
                    BigDecimal totalBudget = catTasks.stream().map(Task::getBudget).reduce(BigDecimal.ZERO, BigDecimal::add);

                    return CategoryMetric.builder()
                            .category(cat)
                            .taskCount(total)
                            .totalBudget(totalBudget)
                            .completionRate(total > 0 ? (completed * 100) / total : 0)
                            .build();
                })
                .sorted(Comparator.comparing(CategoryMetric::getTaskCount).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<UserGrowthMetric> buildUserGrowth(LocalDate start, LocalDate end, List<User> allUsers) {
        return dailyMetricRepository.findByMetricDateBetweenOrderByMetricDateAsc(start, end)
                .stream()
                .map(m -> {
                    int newStudents = (int) allUsers.stream()
                            .filter(u -> u.getCreatedAt() != null && u.getRole() == Role.STUDENT)
                            .filter(u -> u.getCreatedAt().toLocalDate().equals(m.getMetricDate()))
                            .count();
                    int newHirers = (int) allUsers.stream()
                            .filter(u -> u.getCreatedAt() != null && u.getRole() == Role.HIRER)
                            .filter(u -> u.getCreatedAt().toLocalDate().equals(m.getMetricDate()))
                            .count();
                    int total = (int) allUsers.stream()
                            .filter(u -> u.getCreatedAt() != null && !u.getCreatedAt().toLocalDate().isAfter(m.getMetricDate()))
                            .count();

                    return UserGrowthMetric.builder()
                            .date(m.getMetricDate())
                            .newStudents(newStudents)
                            .newHirers(newHirers)
                            .totalUsers(total)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private TaskMetrics buildTaskMetrics(List<Task> allTasks) {
        long byDraft = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DRAFT).count();
        long byActive = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.ACTIVE).count();
        long byInProgress = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long bySubmitted = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.SUBMITTED).count();
        long byCompleted = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        long byDisputed = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DISPUTED).count();

        double avgBudget = allTasks.stream()
                .map(Task::getBudget)
                .filter(b -> b != null)
                .mapToDouble(BigDecimal::doubleValue)
                .average().orElse(0.0);

        double avgDays = allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED && t.getCreatedAt() != null && t.getUpdatedAt() != null)
                .mapToLong(t -> ChronoUnit.DAYS.between(t.getCreatedAt(), t.getUpdatedAt()))
                .average().orElse(0.0);

        return TaskMetrics.builder()
                .total(allTasks.size())
                .byStatusDraft(byDraft)
                .byStatusActive(byActive)
                .byStatusInProgress(byInProgress)
                .byStatusSubmitted(bySubmitted)
                .byStatusCompleted(byCompleted)
                .byStatusDisputed(byDisputed)
                .avgBudget(Math.round(avgBudget * 100.0) / 100.0)
                .avgCompletionDays(Math.round(avgDays * 100.0) / 100.0)
                .build();
    }

    private RevenueMetrics buildRevenueMetrics(List<Task> allTasks) {
        List<Task> completedTasks = allTasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.COMPLETED)
                .toList();

        BigDecimal totalRevenue = completedTasks.stream()
                .map(Task::getBudget)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(EscrowCalculator.getPlatformFeePercent().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        List<Task> thisMonth = completedTasks.stream()
                .filter(t -> t.getUpdatedAt() != null && !t.getUpdatedAt().toLocalDate().isBefore(monthStart))
                .toList();

        BigDecimal monthlyRevenue = thisMonth.stream()
                .map(Task::getBudget)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(EscrowCalculator.getPlatformFeePercent().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        double avgTaskValue = completedTasks.stream()
                .map(Task::getBudget)
                .filter(b -> b != null)
                .mapToDouble(BigDecimal::doubleValue)
                .average().orElse(0.0);

        long totalTasks = taskRepository.count();
        double conversionRate = totalTasks > 0 ? (completedTasks.size() * 100.0) / totalTasks : 0;

        return RevenueMetrics.builder()
                .totalPlatformRevenue(totalRevenue)
                .monthlyRevenue(monthlyRevenue)
                .avgTaskValue(BigDecimal.valueOf(Math.round(avgTaskValue * 100.0) / 100.0))
                .completedTasksThisMonth(thisMonth.size())
                .conversionRate(Math.round(conversionRate * 100.0) / 100.0)
                .build();
    }

    private FreelancerMetrics buildFreelancerMetrics(List<User> allUsers) {
        List<User> freelancers = allUsers.stream().filter(user -> user.getRole() == Role.STUDENT).toList();
        long totalFreelancers = freelancers.size();
        long activeFreelancers = freelancers.stream().filter(u -> Boolean.TRUE.equals(u.getIsAvailable())).count();
        List<Long> freelancerIds = freelancers.stream().map(User::getId).toList();
        Map<Long, Double> ratings = freelancerIds.isEmpty() ? Map.of()
                : reviewRepository.getPublicStatsForUsers(freelancerIds, ReviewType.HIRER_TO_FREELANCER)
                        .stream().collect(Collectors.toMap(
                                row -> ((Number) row[0]).longValue(),
                                row -> ((Number) row[1]).doubleValue()));
        Map<Long, Object[]> taskStats = freelancerIds.isEmpty() ? Map.of()
                : taskRepository.getAssigneeTaskStats(freelancerIds, TaskStatus.COMPLETED)
                        .stream().collect(Collectors.toMap(
                                row -> ((Number) row[0]).longValue(), row -> row));

        double avgRating = ratings.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgEarnings = freelancers.stream().mapToDouble(user -> {
            Object[] stats = taskStats.get(user.getId());
            return stats == null ? 0.0 : ((BigDecimal) stats[2]).doubleValue();
        }).average().orElse(0.0);
        double avgTasks = freelancers.stream().mapToDouble(user -> {
            Object[] stats = taskStats.get(user.getId());
            return stats == null ? 0.0 : ((Number) stats[1]).doubleValue();
        }).average().orElse(0.0);

        return FreelancerMetrics.builder()
                .totalFreelancers(totalFreelancers)
                .activeFreelancers(activeFreelancers)
                .topEarners(0L)
                .avgEarnings(Math.round(avgEarnings * 100.0) / 100.0)
                .avgRating(Math.round(avgRating * 10.0) / 10.0)
                .avgTasksCompleted(Math.round(avgTasks * 10.0) / 10.0)
                .build();
    }

    private DailyMetricResponse toDailyMetricResponse(DailyMetric m) {
        return DailyMetricResponse.builder()
                .date(m.getMetricDate())
                .newUsers(m.getNewUsers())
                .newTasks(m.getNewTasks())
                .completedTasks(m.getCompletedTasks())
                .escrowVolume(m.getTotalEscrowVolume())
                .platformRevenue(m.getTotalPlatformRevenue())
                .activeUsers(m.getActiveUsers())
                .build();
    }
}
