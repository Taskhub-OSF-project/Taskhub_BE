package com.taskhub.repository;

import com.taskhub.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :convId ORDER BY m.createdAt DESC")
    Page<Message> findByConversationId(@Param("convId") Long conversationId, Pageable pageable);

    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true, m.readAt = CURRENT_TIMESTAMP WHERE m.conversation.id = :convId AND m.sender.id != :readerId AND m.isRead = false")
    int markAllAsRead(@Param("convId") Long conversationId, @Param("readerId") Long readerId);

    @Query("""
        SELECT COUNT(m) FROM Message m
        WHERE m.isRead = false
        AND m.sender.id <> :userId
        AND (m.conversation.participantA.id = :userId OR m.conversation.participantB.id = :userId)
        """)
    long countUnreadForUser(@Param("userId") Long userId);

    long countByConversationIdAndIsReadFalseAndSenderIdNot(Long conversationId, Long senderId);
}
