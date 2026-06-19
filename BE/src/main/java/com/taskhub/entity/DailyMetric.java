package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_metrics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DailyMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_date", nullable = false, unique = true)
    private LocalDate metricDate;

    @Column(name = "new_users")
    @Builder.Default
    private Integer newUsers = 0;

    @Column(name = "new_tasks")
    @Builder.Default
    private Integer newTasks = 0;

    @Column(name = "completed_tasks")
    @Builder.Default
    private Integer completedTasks = 0;

    @Column(name = "total_escrow_volume", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalEscrowVolume = BigDecimal.ZERO;

    @Column(name = "total_platform_revenue", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalPlatformRevenue = BigDecimal.ZERO;

    @Column(name = "active_users")
    @Builder.Default
    private Integer activeUsers = 0;

    @Column(name = "disputed_tasks")
    @Builder.Default
    private Integer disputedTasks = 0;
}
