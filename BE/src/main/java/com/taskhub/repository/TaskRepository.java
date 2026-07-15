package com.taskhub.repository;

import com.taskhub.entity.Task;
import com.taskhub.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface TaskRepository extends JpaRepository<Task, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Task t WHERE t.id = :id")
    Optional<Task> findByIdForUpdate(@Param("id") Long id);
    List<Task> findByHirerId(Long hirerId);
    List<Task> findByHirerIdAndStatus(Long hirerId, TaskStatus status);
    List<Task> findByAssignedToId(Long studentId);
    List<Task> findByAssignedToIdAndStatus(Long studentId, TaskStatus status);
    List<Task> findByStatusIn(List<TaskStatus> statuses);
    boolean existsByHirerIdAndStatusIn(Long hirerId, List<TaskStatus> statuses);
    boolean existsByAssignedToIdAndStatusIn(Long studentId, List<TaskStatus> statuses);

    Page<Task> findByHirerId(Long hirerId, Pageable pageable);
    Page<Task> findByHirerIdAndStatus(Long hirerId, TaskStatus status, Pageable pageable);
    Page<Task> findByAssignedToId(Long studentId, Pageable pageable);
    Page<Task> findByAssignedToIdAndStatus(Long studentId, TaskStatus status, Pageable pageable);
    Page<Task> findByStatusIn(List<TaskStatus> statuses, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.status = :status AND t.hirer.id != :excludeUserId AND (t.deadline IS NULL OR t.deadline > CURRENT_TIMESTAMP) ORDER BY t.createdAt DESC")
    Page<Task> findAvailableTasks(@Param("status") TaskStatus status, @Param("excludeUserId") Long excludeUserId, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.status IN :statuses AND t.hirer.id != :excludeUserId AND (t.deadline IS NULL OR t.deadline > CURRENT_TIMESTAMP) ORDER BY t.createdAt DESC")
    Page<Task> findAvailableTasks(@Param("statuses") List<TaskStatus> statuses, @Param("excludeUserId") Long excludeUserId, Pageable pageable);

    Page<Task> findByStatus(TaskStatus status, Pageable pageable);

    @Query(value = """
        SELECT DISTINCT t FROM Task t LEFT JOIN t.skillsRequired skill
        WHERE t.status = :status
          AND t.deadline > :now
          AND (:category IS NULL OR LOWER(t.category) = LOWER(:category))
          AND (:keyword IS NULL
               OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(t.category) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(skill) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """,
        countQuery = """
        SELECT COUNT(DISTINCT t.id) FROM Task t LEFT JOIN t.skillsRequired skill
        WHERE t.status = :status
          AND t.deadline > :now
          AND (:category IS NULL OR LOWER(t.category) = LOWER(:category))
          AND (:keyword IS NULL
               OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(t.category) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(skill) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    Page<Task> searchPublicTasks(@Param("status") TaskStatus status,
                                 @Param("keyword") String keyword,
                                 @Param("category") String category,
                                 @Param("now") LocalDateTime now,
                                 Pageable pageable);

    @Query("""
        SELECT t.assignedTo.id, COUNT(t), COALESCE(SUM(t.budget), 0)
        FROM Task t
        WHERE t.assignedTo.id IN :userIds AND t.status = :status
        GROUP BY t.assignedTo.id
        """)
    List<Object[]> getAssigneeTaskStats(@Param("userIds") List<Long> userIds,
                                        @Param("status") TaskStatus status);
}
