package com.taskhub.repository;

import com.taskhub.entity.Task;
import com.taskhub.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByHirerId(UUID hirerId);
    List<Task> findByAssignedToId(UUID studentId);
    List<Task> findByStatusIn(List<TaskStatus> statuses);
}
