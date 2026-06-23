package com.taskhub.repository;

import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    Page<User> findByRole(Role role, Pageable pageable);

    @Query("""
        SELECT u FROM User u
        WHERE u.role = :role
        AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(u.university) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(u.major) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    Page<User> searchByKeyword(@Param("role") Role role, @Param("keyword") String keyword, Pageable pageable);

    @Query("""
        SELECT COUNT(u) FROM User u WHERE u.role = :role
        """)
    long countByRole(@Param("role") Role role);
}
