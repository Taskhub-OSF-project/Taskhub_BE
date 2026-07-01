package com.taskhub.repository;

import com.taskhub.entity.AiChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {
    List<AiChatSession> findByUserIdOrderByCreatedAtDesc(String userId);
    List<AiChatSession> findByUserIdAndSessionTypeOrderByCreatedAtDesc(String userId, String sessionType);
}
