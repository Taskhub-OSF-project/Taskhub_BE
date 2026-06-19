package com.taskhub.repository;

import com.taskhub.entity.DisputeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DisputeEventRepository extends JpaRepository<DisputeEvent, Long> {
    List<DisputeEvent> findByTaskIdOrderByCreatedAtAsc(Long taskId);
}
