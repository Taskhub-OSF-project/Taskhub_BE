package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Base entity with audit fields. Subclasses must define their own @Id field
 * (e.g. "private Long id;" with @Id @GeneratedValue) to avoid JPA proxy conflicts.
 *
 * Use @Getter @Setter (not @Data) to avoid Lombok generating equals/hashCode
 * that triggers lazy-load proxies in JPA relationships.
 */
@MappedSuperclass
@Getter @Setter
public abstract class BaseEntity {
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
