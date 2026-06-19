package com.taskhub.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AnalyticsDashboardResponse {
    private PlatformOverview overview;
    private List<DailyMetricResponse> dailyMetrics;
    private List<CategoryMetric> topCategories;
    private List<UserGrowthMetric> userGrowth;
    private TaskMetrics taskMetrics;
    private RevenueMetrics revenueMetrics;
    private FreelancerMetrics freelancerMetrics;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PlatformOverview {
        private long totalUsers;
        private long totalStudents;
        private long totalHirers;
        private long totalTasks;
        private long activeTasks;
        private long completedTasks;
        private long disputedTasks;
        private BigDecimal totalEscrowVolume;
        private BigDecimal totalPlatformRevenue;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyMetricResponse {
        private LocalDate date;
        private int newUsers;
        private int newTasks;
        private int completedTasks;
        private BigDecimal escrowVolume;
        private BigDecimal platformRevenue;
        private int activeUsers;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CategoryMetric {
        private String category;
        private int taskCount;
        private BigDecimal totalBudget;
        private int completionRate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserGrowthMetric {
        private LocalDate date;
        private int newStudents;
        private int newHirers;
        private int totalUsers;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TaskMetrics {
        private long total;
        private long byStatusDraft;
        private long byStatusActive;
        private long byStatusInProgress;
        private long byStatusSubmitted;
        private long byStatusCompleted;
        private long byStatusDisputed;
        private double avgBudget;
        private double avgCompletionDays;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RevenueMetrics {
        private BigDecimal totalPlatformRevenue;
        private BigDecimal monthlyRevenue;
        private BigDecimal avgTaskValue;
        private long completedTasksThisMonth;
        private double conversionRate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FreelancerMetrics {
        private long totalFreelancers;
        private long activeFreelancers;
        private long topEarners;
        private double avgEarnings;
        private double avgRating;
        private double avgTasksCompleted;
    }
}