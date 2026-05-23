package com.taskhub.repository;

import com.taskhub.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
