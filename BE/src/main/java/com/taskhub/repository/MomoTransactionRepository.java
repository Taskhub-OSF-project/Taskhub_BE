package com.taskhub.repository;

import com.taskhub.entity.MomoTransaction;
import com.taskhub.enums.MomoTransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MomoTransactionRepository extends JpaRepository<MomoTransaction, Long> {

    Optional<MomoTransaction> findByOrderId(String orderId);

    boolean existsByOrderIdAndStatus(String orderId, MomoTransactionStatus status);

    List<MomoTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<MomoTransaction> findByTypeAndStatus(com.taskhub.enums.MomoTransactionType type, MomoTransactionStatus status, Pageable pageable);
    
    Page<MomoTransaction> findByType(com.taskhub.enums.MomoTransactionType type, Pageable pageable);
}
