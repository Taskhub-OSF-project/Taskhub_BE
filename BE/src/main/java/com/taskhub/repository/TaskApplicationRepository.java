package com.taskhub.repository;

import com.taskhub.entity.TaskApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskApplicationRepository extends JpaRepository<TaskApplication, Long> {
    List<TaskApplication> findByTaskId(Long taskId);
    List<TaskApplication> findByStudentId(Long studentId);
    Optional<TaskApplication> findByTaskIdAndStudentId(Long taskId, Long studentId);
    boolean existsByTaskIdAndStudentId(Long taskId, Long studentId);
}
