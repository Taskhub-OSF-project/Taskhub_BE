package com.taskhub.repository;

import com.taskhub.entity.PayoutRequest;
import com.taskhub.enums.PayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {
    Page<PayoutRequest> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<PayoutRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<PayoutRequest> findByStatusOrderByCreatedAtDesc(PayoutStatus status, Pageable pageable);
    long countByStatus(PayoutStatus status);
}
