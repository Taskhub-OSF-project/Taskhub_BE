package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"task_id", "participant_a_id", "participant_b_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_a_id", nullable = false)
    private User participantA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_b_id", nullable = false)
    private User participantB;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "last_message_preview", length = 255)
    private String lastMessagePreview;

    @Column(name = "unread_count_a")
    @Builder.Default
    private Integer unreadCountA = 0;

    @Column(name = "unread_count_b")
    @Builder.Default
    private Integer unreadCountB = 0;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public void incrementUnread(boolean forParticipantA) {
        if (forParticipantA) {
            unreadCountA = (unreadCountA == null ? 0 : unreadCountA) + 1;
        } else {
            unreadCountB = (unreadCountB == null ? 0 : unreadCountB) + 1;
        }
    }

    public void resetUnread(boolean forParticipantA) {
        if (forParticipantA) {
            unreadCountA = 0;
        } else {
            unreadCountB = 0;
        }
    }

    public int getUnreadCountFor(Long userId) {
        if (participantA.getId().equals(userId)) return unreadCountA != null ? unreadCountA : 0;
        if (participantB.getId().equals(userId)) return unreadCountB != null ? unreadCountB : 0;
        return 0;
    }
}
