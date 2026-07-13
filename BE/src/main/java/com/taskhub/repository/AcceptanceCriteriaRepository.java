package com.taskhub.repository;

import com.taskhub.entity.AcceptanceCriteria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AcceptanceCriteriaRepository extends JpaRepository<AcceptanceCriteria, Long> {
    List<AcceptanceCriteria> findByTaskId(Long taskId);
    List<AcceptanceCriteria> findByTaskIdOrderByIdAsc(Long taskId);
}
