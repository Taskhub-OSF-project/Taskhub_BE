package com.taskhub.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_chat_messages",
       indexes = {
           @Index(name = "idx_message_session", columnList = "sessionId"),
           @Index(name = "idx_message_created", columnList = "createdAt")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private String role; // USER, AI, SYSTEM

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "TEXT")
    private String attachmentRef; // file URL or reference

    private String attachmentType; // pdf, docx, image, etc.

    @Column(columnDefinition = "TEXT")
    private String metadataJson; // JSON for extracted data, ratings, etc.

    @CreationTimestamp
    private LocalDateTime createdAt;
}
