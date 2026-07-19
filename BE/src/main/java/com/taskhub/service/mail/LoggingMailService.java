package com.taskhub.service.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Impl mặc định: chỉ ghi link ra log thay vì gửi email thật.
 * Dùng cho dev/prototype khi chưa cấu hình SMTP. Production thêm một
 * {@link MailService} SMTP và đánh dấu {@code @Primary} để thay thế bean này.
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "app.mail.delivery-enabled", havingValue = "false", matchIfMissing = true)
public class LoggingMailService implements MailService {

    @Override
    public boolean isDeliveryEnabled() {
        return false;
    }

    @Override
    public void sendPasswordReset(String toEmail, String resetLink) {
        log.info("Password reset email skipped because delivery is disabled (recipient={})", mask(toEmail));
    }

    @Override
    public void sendEmailVerification(String toEmail, String verifyLink) {
        log.info("Verification email skipped because delivery is disabled (recipient={})", mask(toEmail));
    }

    @Override
    public void sendRegistrationOtp(String toEmail, String code) {
        log.info("Registration OTP email skipped because delivery is disabled (recipient={})", mask(toEmail));
    }

    @Override
    public void sendLoginOtp(String toEmail, String code) {
        log.info("Login OTP email skipped because delivery is disabled (recipient={})", mask(toEmail));
    }

    private String mask(String email) {
        if (email == null || !email.contains("@")) return "***";
        return email.charAt(0) + "***" + email.substring(email.indexOf('@'));
    }
}
