package com.taskhub.repository;

import com.taskhub.entity.AiCriteriaSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiCriteriaSuggestionRepository extends JpaRepository<AiCriteriaSuggestion, Long> {
    List<AiCriteriaSuggestion> findByTaskIdOrderByOrderIndexAsc(Long taskId);
    List<AiCriteriaSuggestion> findByTaskIdAndIsActive(Long taskId, Boolean isActive);
}
