package com.taskhub.repository;

import com.taskhub.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<WalletTransaction> findByUserId(Long userId, Pageable pageable);
    long countByUserId(Long userId);
}
