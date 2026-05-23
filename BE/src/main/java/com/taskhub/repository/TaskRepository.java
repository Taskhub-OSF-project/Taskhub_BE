package com.taskhub.repository;

import com.taskhub.entity.Task;
import com.taskhub.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByHirerId(Long hirerId);
    List<Task> findByAssignedToId(Long studentId);
    List<Task> findByStatusIn(List<TaskStatus> statuses);
}
