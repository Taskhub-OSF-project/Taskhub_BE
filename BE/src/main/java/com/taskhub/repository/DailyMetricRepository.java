package com.taskhub.repository;

import com.taskhub.entity.DailyMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyMetricRepository extends JpaRepository<DailyMetric, Long> {
    Optional<DailyMetric> findByMetricDate(LocalDate date);

    List<DailyMetric> findByMetricDateBetweenOrderByMetricDateAsc(LocalDate start, LocalDate end);

    @Query("SELECT COALESCE(SUM(d.totalEscrowVolume), 0) FROM DailyMetric d")
    BigDecimal getTotalEscrowVolume();

    @Query("SELECT COALESCE(SUM(d.totalPlatformRevenue), 0) FROM DailyMetric d")
    BigDecimal getTotalPlatformRevenue();
}
