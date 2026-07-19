package com.taskhub.repository;

import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByPhone(String phone);
    Optional<User> findByAuthProviderAndProviderSubject(String authProvider, String providerSubject);

    Page<User> findByRole(Role role, Pageable pageable);
    java.util.List<User> findByRole(Role role);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT u.id FROM User u WHERE u.role <> com.taskhub.enums.Role.ADMIN ORDER BY u.id")
    Slice<Long> findBroadcastRecipientIds(Pageable pageable);

    @Query("SELECT u.id FROM User u WHERE u.role = :role ORDER BY u.id")
    Slice<Long> findBroadcastRecipientIdsByRole(@Param("role") Role role, Pageable pageable);
}
