package com.taskhub.repository;

import com.taskhub.entity.Task;
import com.taskhub.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByHirerId(Long hirerId);
    List<Task> findByHirerIdAndStatus(Long hirerId, TaskStatus status);
    List<Task> findByAssignedToId(Long studentId);
    List<Task> findByAssignedToIdAndStatus(Long studentId, TaskStatus status);
    List<Task> findByStatusIn(List<TaskStatus> statuses);
}
