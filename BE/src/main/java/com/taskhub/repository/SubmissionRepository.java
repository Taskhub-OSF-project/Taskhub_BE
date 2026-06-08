package com.taskhub.repository;

import com.taskhub.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByTaskId(Long taskId);
    List<Submission> findByStudentId(Long studentId);
    Optional<Submission> findTopByTaskIdOrderBySubmittedAtDesc(Long taskId);
}
