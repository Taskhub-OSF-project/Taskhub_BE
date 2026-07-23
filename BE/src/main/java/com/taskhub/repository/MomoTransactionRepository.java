package com.taskhub.repository;

import com.taskhub.entity.MomoTransaction;
import com.taskhub.enums.MomoTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MomoTransactionRepository extends JpaRepository<MomoTransaction, Long> {

    Optional<MomoTransaction> findByOrderId(String orderId);

    boolean existsByOrderIdAndStatus(String orderId, MomoTransactionStatus status);

    List<MomoTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}
