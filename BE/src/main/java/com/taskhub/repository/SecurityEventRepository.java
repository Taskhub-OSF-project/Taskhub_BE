package com.taskhub.repository;

import com.taskhub.entity.SecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    Page<SecurityEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<SecurityEvent> findByEventTypeOrderByCreatedAtDesc(
            SecurityEvent.EventType eventType, Pageable pageable);

    Page<SecurityEvent> findByOutcomeOrderByCreatedAtDesc(String outcome, Pageable pageable);

    @Query("SELECT e FROM SecurityEvent e WHERE e.createdAt >= :since ORDER BY e.createdAt DESC")
    Page<SecurityEvent> findRecentEvents(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT e FROM SecurityEvent e WHERE " +
           "(:userId IS NULL OR e.user.id = :userId) AND " +
           "(:eventType IS NULL OR e.eventType = :eventType) AND " +
           "(:outcome IS NULL OR e.outcome = :outcome) AND " +
           "(:from IS NULL OR e.createdAt >= :from) AND " +
           "(:to IS NULL OR e.createdAt <= :to) " +
           "ORDER BY e.createdAt DESC")
    Page<SecurityEvent> searchEvents(
            @Param("userId") Long userId,
            @Param("eventType") SecurityEvent.EventType eventType,
            @Param("outcome") String outcome,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("SELECT COUNT(e) FROM SecurityEvent e WHERE e.eventType = :eventType AND e.createdAt >= :since")
    long countByEventTypeSince(@Param("eventType") SecurityEvent.EventType eventType,
                                @Param("since") LocalDateTime since);

    @Query("SELECT e FROM SecurityEvent e WHERE e.ipAddress = :ip AND e.createdAt >= :since ORDER BY e.createdAt DESC")
    List<SecurityEvent> findByIpSince(@Param("ip") String ip, @Param("since") LocalDateTime since, Pageable pageable);
}
