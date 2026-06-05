package com.taskhub.repository;

import com.taskhub.entity.RevisionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RevisionRequestRepository extends JpaRepository<RevisionRequest, Long> {
    List<RevisionRequest> findByTaskIdOrderByCreatedAtAsc(Long taskId);
    Optional<RevisionRequest> findTopByTaskIdOrderByCreatedAtDesc(Long taskId);
    long countByTaskId(Long taskId);
}
