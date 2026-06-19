package com.taskhub.repository;

import com.taskhub.entity.TaskApplication;
import com.taskhub.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskApplicationRepository extends JpaRepository<TaskApplication, Long> {
    List<TaskApplication> findByTaskId(Long taskId);
    List<TaskApplication> findByStudentId(Long studentId);
    List<TaskApplication> findByStudentIdAndStatus(Long studentId, ApplicationStatus status);
    Optional<TaskApplication> findByTaskIdAndStudentId(Long taskId, Long studentId);
    boolean existsByTaskIdAndStudentId(Long taskId, Long studentId);
    boolean existsByTaskId(Long taskId);

    Page<TaskApplication> findByTaskId(Long taskId, Pageable pageable);
    Page<TaskApplication> findByStudentId(Long studentId, Pageable pageable);
    Page<TaskApplication> findByStudentIdAndStatus(Long studentId, ApplicationStatus status, Pageable pageable);
    long countByTaskId(Long taskId);
    long countByStudentId(Long studentId);
}
