package com.taskhub.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminDashboardResponse {
    private long totalUsers;
    private long totalStudents;
    private long totalHirers;
    private long totalTasks;
    private long activeTasks;
    private long completedTasks;
    private long disputedTasks;
    private BigDecimal totalPlatformVolume;
}