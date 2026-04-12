package com.taskhub.repository;

import com.taskhub.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    List<Submission> findByTaskId(UUID taskId);
    List<Submission> findByStudentId(UUID studentId);
}
