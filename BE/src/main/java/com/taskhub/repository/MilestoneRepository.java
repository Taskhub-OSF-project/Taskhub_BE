package com.taskhub.repository;

import com.taskhub.entity.Milestone;
import com.taskhub.enums.EscrowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findByTaskIdOrderByDisplayOrder(Long taskId);

    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM Milestone m WHERE m.task.id = :taskId")
    BigDecimal sumAmountByTaskId(@Param("taskId") Long taskId);

    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM Milestone m WHERE m.task.id = :taskId AND m.escrowStatus = :status")
    BigDecimal sumAmountByTaskIdAndEscrowStatus(@Param("taskId") Long taskId, @Param("status") EscrowStatus status);

    @Query("SELECT COUNT(m) FROM Milestone m WHERE m.task.id = :taskId AND m.escrowStatus = :status")
    long countByTaskIdAndEscrowStatus(@Param("taskId") Long taskId, @Param("status") EscrowStatus status);

    Optional<Milestone> findByIdAndTaskId(Long id, Long taskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Milestone m WHERE m.id = :id AND m.task.id = :taskId")
    Optional<Milestone> findByIdAndTaskIdForUpdate(@Param("id") Long id, @Param("taskId") Long taskId);

    @Modifying
    @Query("UPDATE Milestone m SET m.escrowStatus = :newStatus WHERE m.task.id = :taskId AND m.escrowStatus = :currentStatus")
    int updateEscrowStatusByTaskId(@Param("taskId") Long taskId, @Param("currentStatus") EscrowStatus currentStatus, @Param("newStatus") EscrowStatus newStatus);
}
