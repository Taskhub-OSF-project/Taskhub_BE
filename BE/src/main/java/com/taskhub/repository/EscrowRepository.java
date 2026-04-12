package com.taskhub.repository;

import com.taskhub.entity.Escrow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EscrowRepository extends JpaRepository<Escrow, UUID> {
    Optional<Escrow> findByTaskId(UUID taskId);
}
