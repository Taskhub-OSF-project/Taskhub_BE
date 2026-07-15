package com.taskhub.repository;

import com.taskhub.entity.TaskApplication;
import com.taskhub.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface TaskApplicationRepository extends JpaRepository<TaskApplication, Long> {
    List<TaskApplication> findByTaskId(Long taskId);
    List<TaskApplication> findByStudentId(Long studentId);
    List<TaskApplication> findByStudentIdAndStatus(Long studentId, ApplicationStatus status);
    Optional<TaskApplication> findByTaskIdAndStudentId(Long taskId, Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM TaskApplication a WHERE a.id = :id")
    Optional<TaskApplication> findByIdForUpdate(@Param("id") Long id);
    boolean existsByTaskIdAndStudentId(Long taskId, Long studentId);
    boolean existsByTaskId(Long taskId);
    boolean existsByStudentIdAndStatus(Long studentId, ApplicationStatus status);

    Page<TaskApplication> findByTaskId(Long taskId, Pageable pageable);
    Page<TaskApplication> findByStudentId(Long studentId, Pageable pageable);
    Page<TaskApplication> findByStudentIdAndStatus(Long studentId, ApplicationStatus status, Pageable pageable);
    long countByTaskId(Long taskId);
    long countByStudentId(Long studentId);
}
