package com.taskhub.repository;

import com.taskhub.entity.EvaluationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRecordRepository extends JpaRepository<EvaluationRecord, Long> {
    List<EvaluationRecord> findBySubmissionIdOrderByIdAsc(Long submissionId);
    List<EvaluationRecord> findBySubmissionId(Long submissionId);
    Optional<EvaluationRecord> findBySubmissionIdAndCriteriaId(Long submissionId, Long criteriaId);
    void deleteBySubmissionId(Long submissionId);
}
