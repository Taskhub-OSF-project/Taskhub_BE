package com.taskhub.config;

import com.taskhub.service.mail.MailService;
import com.taskhub.service.mail.LoggingMailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Provides lightweight implementations for external services in tests.
 * Email is not sent (logs only); audit events are still persisted.
 */
@TestConfiguration
public class TestConfig {
    @Bean
    @Primary
    public MailService mailService() {
        return new LoggingMailService();
    }
}
