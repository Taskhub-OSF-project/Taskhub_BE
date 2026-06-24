package com.taskhub.repository;

import com.taskhub.entity.SecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {
    Page<SecurityEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<SecurityEvent> findByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);
}
