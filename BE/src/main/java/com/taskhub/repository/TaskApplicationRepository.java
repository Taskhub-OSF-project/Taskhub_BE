package com.taskhub.repository;

import com.taskhub.entity.TaskApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskApplicationRepository extends JpaRepository<TaskApplication, UUID> {
    List<TaskApplication> findByTaskId(UUID taskId);
    List<TaskApplication> findByStudentId(UUID studentId);
    Optional<TaskApplication> findByTaskIdAndStudentId(UUID taskId, UUID studentId);
    boolean existsByTaskIdAndStudentId(UUID taskId, UUID studentId);
}
