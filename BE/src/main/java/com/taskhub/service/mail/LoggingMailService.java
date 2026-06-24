package com.taskhub.service.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Impl mặc định: chỉ ghi link ra log thay vì gửi email thật.
 * Dùng cho dev/prototype khi chưa cấu hình SMTP. Production thêm một
 * {@link MailService} SMTP và đánh dấu {@code @Primary} để thay thế bean này.
 */
@Slf4j
@Service
@Primary
public class LoggingMailService implements MailService {

    @Override
    public void sendPasswordReset(String toEmail, String resetLink) {
        log.warn("[MAIL:DEV] Password reset for {} -> {}", toEmail, resetLink);
    }

    @Override
    public void sendEmailVerification(String toEmail, String verifyLink) {
        log.warn("[MAIL:DEV] Email verification for {} -> {}", toEmail, verifyLink);
    }
}
