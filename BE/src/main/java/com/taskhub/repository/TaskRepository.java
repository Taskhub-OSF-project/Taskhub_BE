package com.taskhub.repository;

import com.taskhub.entity.Task;
import com.taskhub.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByHirerId(Long hirerId);
    List<Task> findByHirerIdAndStatus(Long hirerId, TaskStatus status);
    List<Task> findByAssignedToId(Long studentId);
    List<Task> findByAssignedToIdAndStatus(Long studentId, TaskStatus status);
    List<Task> findByStatusIn(List<TaskStatus> statuses);

    Page<Task> findByHirerId(Long hirerId, Pageable pageable);
    Page<Task> findByHirerIdAndStatus(Long hirerId, TaskStatus status, Pageable pageable);
    Page<Task> findByAssignedToId(Long studentId, Pageable pageable);
    Page<Task> findByAssignedToIdAndStatus(Long studentId, TaskStatus status, Pageable pageable);
    Page<Task> findByStatusIn(List<TaskStatus> statuses, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.status = :status AND t.hirer.id != :excludeUserId ORDER BY t.createdAt DESC")
    Page<Task> findAvailableTasks(@Param("status") TaskStatus status, @Param("excludeUserId") Long excludeUserId, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.status IN :statuses AND t.hirer.id != :excludeUserId ORDER BY t.createdAt DESC")
    Page<Task> findAvailableTasks(@Param("statuses") List<TaskStatus> statuses, @Param("excludeUserId") Long excludeUserId, Pageable pageable);

    Page<Task> findByStatus(TaskStatus status, Pageable pageable);
}
