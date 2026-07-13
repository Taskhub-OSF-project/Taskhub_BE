package com.taskhub.repository;

import com.taskhub.entity.TaskRemovalRequest;
import com.taskhub.enums.RemovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRemovalRequestRepository extends JpaRepository<TaskRemovalRequest, Long> {

    Page<TaskRemovalRequest> findByStatusOrderByCreatedAtDesc(RemovalStatus status, Pageable pageable);

    Page<TaskRemovalRequest> findByRequestedByIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<TaskRemovalRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<TaskRemovalRequest> findByTaskId(Long taskId);

    Optional<TaskRemovalRequest> findByTaskIdAndStatus(Long taskId, RemovalStatus status);

    boolean existsByTaskIdAndStatus(Long taskId, RemovalStatus status);

    @Query("SELECT trr FROM TaskRemovalRequest trr WHERE trr.status = :status ORDER BY trr.createdAt DESC")
    List<TaskRemovalRequest> findPendingRequests(@Param("status") RemovalStatus status);

    @Query("SELECT COUNT(trr) FROM TaskRemovalRequest trr WHERE trr.status = :status")
    long countByStatus(@Param("status") RemovalStatus status);

    @Query("SELECT trr FROM TaskRemovalRequest trr JOIN FETCH trr.task t JOIN FETCH trr.requestedBy WHERE trr.id = :id")
    Optional<TaskRemovalRequest> findByIdWithDetails(@Param("id") Long id);
}
