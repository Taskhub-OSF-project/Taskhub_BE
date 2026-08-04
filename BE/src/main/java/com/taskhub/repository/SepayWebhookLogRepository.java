package com.taskhub.repository;

import com.taskhub.entity.SepayWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SepayWebhookLogRepository extends JpaRepository<SepayWebhookLog, Long> {
    Optional<SepayWebhookLog> findByReferenceNumber(String referenceNumber);
}
