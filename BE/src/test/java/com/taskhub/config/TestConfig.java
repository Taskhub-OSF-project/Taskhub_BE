package com.taskhub.config;

import com.taskhub.service.AuditService;
import com.taskhub.service.EmailService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Provides lightweight mocks for external services in tests.
 * Email is not sent; audit events are not persisted.
 */
@TestConfiguration
public class TestConfig {
    @Bean
    @Primary
    public EmailService emailService() {
        return Mockito.mock(EmailService.class);
    }

    @Bean
    @Primary
    public AuditService auditService() {
        return Mockito.mock(AuditService.class);
    }
}
