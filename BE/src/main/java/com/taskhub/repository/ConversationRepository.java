package com.taskhub.repository;

import com.taskhub.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("""
        SELECT c FROM Conversation c
        WHERE (c.participantA.id = :userId OR c.participantB.id = :userId)
        ORDER BY c.lastMessageAt DESC NULLS LAST, c.createdAt DESC
        """)
    Page<Conversation> findByParticipantId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT c FROM Conversation c
        WHERE (c.participantA.id = :userId OR c.participantB.id = :userId)
        ORDER BY c.lastMessageAt DESC NULLS LAST, c.createdAt DESC
        """)
    List<Conversation> findByParticipantIdOrderByLastMessage(@Param("userId") Long userId);

    @Query("""
        SELECT c FROM Conversation c
        WHERE c.task.id = :taskId
        AND ((c.participantA.id = :userA AND c.participantB.id = :userB)
             OR (c.participantA.id = :userB AND c.participantB.id = :userA))
        """)
    Optional<Conversation> findByTaskAndParticipants(
            @Param("taskId") Long taskId,
            @Param("userA") Long userA,
            @Param("userB") Long userB);

    @Query("""
        SELECT COUNT(c) FROM Conversation c
        WHERE (c.participantA.id = :userId OR c.participantB.id = :userId)
        AND ((c.unreadCountA > 0 AND c.participantA.id = :userId) OR (c.unreadCountB > 0 AND c.participantB.id = :userId))
        """)
    long countUnreadForUser(@Param("userId") Long userId);

    @Query("""
        SELECT c FROM Conversation c
        WHERE c.id = :id
        AND (c.participantA.id = :participantId OR c.participantB.id = :participantId)
        """)
    Optional<Conversation> findByIdAndParticipantId(@Param("id") Long id, @Param("participantId") Long participantId);
}
