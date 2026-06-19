package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PortfolioItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "project_url", length = 500)
    private String projectUrl;

    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrlsJson;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPublic = true;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
