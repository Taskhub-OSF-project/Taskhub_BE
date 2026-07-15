package com.taskhub.repository;

import com.taskhub.entity.Review;
import com.taskhub.enums.ReviewType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByRevieweeIdAndIsPublicTrueOrderByCreatedAtDesc(Long revieweeId, Pageable pageable);

    Page<Review> findByIsPublicTrueOrderByCreatedAtDesc(Pageable pageable);

    Page<Review> findByRevieweeIdAndTypeOrderByCreatedAtDesc(Long revieweeId, ReviewType type, Pageable pageable);

    Optional<Review> findByTaskIdAndReviewerIdAndType(Long taskId, Long reviewerId, ReviewType type);

    boolean existsByTaskIdAndReviewerIdAndType(Long taskId, Long reviewerId, ReviewType type);

    long countByRevieweeIdAndType(Long revieweeId, ReviewType type);

    @Query("""
        SELECT AVG(r.rating) FROM Review r
        WHERE r.reviewee.id = :userId AND r.type = :type AND r.isPublic = true
        """)
    Double getAverageRating(@Param("userId") Long userId, @Param("type") ReviewType type);

    @Query("""
        SELECT COUNT(r) FROM Review r
        WHERE r.reviewee.id = :userId AND r.type = :type AND r.rating >= 4 AND r.isPublic = true
        """)
    long countFiveStars(@Param("userId") Long userId, @Param("type") ReviewType type);

    @Query("""
        SELECT r FROM Review r
        WHERE r.task.id = :taskId
        """)
    java.util.List<Review> findByTaskId(@Param("taskId") Long taskId);

    @Query("""
        SELECT r.reviewee.id, AVG(r.rating), COUNT(r) FROM Review r
        WHERE r.reviewee.id IN :userIds AND r.type = :type AND r.isPublic = true
        GROUP BY r.reviewee.id
        """)
    List<Object[]> getPublicStatsForUsers(@Param("userIds") List<Long> userIds,
                                          @Param("type") ReviewType type);
}
