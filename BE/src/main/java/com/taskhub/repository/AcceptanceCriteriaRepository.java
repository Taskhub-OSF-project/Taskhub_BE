package com.taskhub.repository;

import com.taskhub.entity.AcceptanceCriteria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AcceptanceCriteriaRepository extends JpaRepository<AcceptanceCriteria, UUID> {
    List<AcceptanceCriteria> findByTaskId(UUID taskId);
}
