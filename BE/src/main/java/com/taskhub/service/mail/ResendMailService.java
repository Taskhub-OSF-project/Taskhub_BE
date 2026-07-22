package com.taskhub.service.mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.exception.TaskHubException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends security emails through Resend while SES production access is pending.
 * OTP generation, hashing, expiry, retries, and verification remain in AuthService.
 */
@Service
@ConditionalOnExpression("'${app.mail.delivery-enabled:false}' == 'true' and '${app.mail.provider:ses}' == 'resend'")
@Slf4j
public class ResendMailService implements MailService {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String fromEmail;

    @Autowired
    public ResendMailService(
            ObjectMapper objectMapper,
            @Value("${app.mail.resend.api-key:}") String apiKey,
            @Value("${app.mail.from-email:}") String fromEmail,
            @Value("${app.mail.resend.endpoint:https://api.resend.com/emails}") String endpoint) {
        this(objectMapper, HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build(),
                apiKey, fromEmail, URI.create(endpoint));
    }

    ResendMailService(ObjectMapper objectMapper, HttpClient httpClient,
                      String apiKey, String fromEmail, URI endpoint) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("APP_MAIL_RESEND_API_KEY is required when Resend delivery is enabled");
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("APP_MAIL_FROM_EMAIL is required when Resend delivery is enabled");
        }
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey.trim();
        this.fromEmail = fromEmail.trim();
    }

    @Override
    public boolean isDeliveryEnabled() {
        return true;
    }

    @Override
    public void sendPasswordReset(String toEmail, String resetLink) {
        send(toEmail, "Đặt lại mật khẩu TaskHub",
                "Dùng liên kết một lần này để đặt lại mật khẩu TaskHub. Liên kết hết hạn sau 1 giờ:\n\n"
                        + resetLink + "\n\nNếu bạn không yêu cầu, hãy bỏ qua email này.");
    }

    @Override
    public void sendEmailVerification(String toEmail, String verifyLink) {
        send(toEmail, "Xác minh email TaskHub",
                "Dùng liên kết một lần này để xác minh email TaskHub. Liên kết hết hạn sau 24 giờ:\n\n"
                        + verifyLink + "\n\nNếu bạn không tạo tài khoản, hãy bỏ qua email này.");
    }

    @Override
    public void sendRegistrationOtp(String toEmail, String code) {
        send(toEmail, "Mã xác minh đăng ký TaskHub",
                "Mã OTP đăng ký TaskHub của bạn là: " + code
                        + "\n\nMã có hiệu lực trong 10 phút. Không chia sẻ mã này với bất kỳ ai."
                        + "\n\nNếu bạn không tạo tài khoản, hãy bỏ qua email này.");
    }

    @Override
    public void sendLoginOtp(String toEmail, String code) {
        send(toEmail, "Mã đăng nhập TaskHub",
                "Mã OTP đăng nhập TaskHub của bạn là: " + code
                        + "\n\nMã có hiệu lực trong 10 phút. Không chia sẻ mã này với bất kỳ ai."
                        + "\n\nNếu bạn không đăng nhập, hãy đổi mật khẩu ngay.");
    }

    private void send(String toEmail, String subject, String text) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", "TaskHub/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(toEmail, subject, text)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Resend could not deliver a security email (status={})", response.statusCode());
                throw deliveryFailure();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Resend security email request was interrupted");
            throw deliveryFailure();
        } catch (IOException ex) {
            log.warn("Resend security email request failed ({})", ex.getClass().getSimpleName());
            throw deliveryFailure();
        }
    }

    private String toJson(String toEmail, String subject, String text) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "from", fromEmail,
                    "to", List.of(toEmail),
                    "subject", subject,
                    "text", text));
        } catch (JsonProcessingException ex) {
            throw TaskHubException.internalError("Không thể chuẩn bị email bảo mật");
        }
    }

    private TaskHubException deliveryFailure() {
        return TaskHubException.internalError(
                "Không thể gửi mã bảo mật tới email này. Vui lòng kiểm tra địa chỉ email và thử lại.");
    }
}
