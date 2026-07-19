package com.taskhub.service.mail;

/**
 * Trừu tượng hóa việc gửi email cho các luồng bảo mật.
 * Impl mặc định là {@link LoggingMailService} (ghi log). Production cắm SMTP
 * bằng một bean khác (profile-gated) thay thế bean này.
 */
public interface MailService {

    boolean isDeliveryEnabled();

    /**
     * Gửi link đặt lại mật khẩu.
     *
     * @param toEmail   email người nhận
     * @param resetLink link kèm raw token (chỉ tồn tại trong email)
     */
    void sendPasswordReset(String toEmail, String resetLink);

    /**
     * Gửi link xác thực email.
     */
    void sendEmailVerification(String toEmail, String verifyLink);

    void sendRegistrationOtp(String toEmail, String code);

    void sendLoginOtp(String toEmail, String code);
}
