package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_chat_sessions",
       indexes = @Index(name = "idx_session_user", columnList = "userId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String sessionType; // CHAT, EVALUATION, CRITERIA, DISPUTE, PROGRESS

    private String taskId;

    @Column(columnDefinition = "TEXT")
    private String contextSummary; // JSON snapshot of relevant task/submission context

    @Column(columnDefinition = "TEXT")
    private String userProfileJson; // JSON snapshot of user profile at session start

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    @Builder.Default
    private Integer messageCount = 0;

    private LocalDateTime lastActiveAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
