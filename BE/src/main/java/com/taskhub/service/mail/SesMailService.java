package com.taskhub.service.mail;

import com.taskhub.exception.TaskHubException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

@Service
@ConditionalOnProperty(name = "app.mail.delivery-enabled", havingValue = "true")
public class SesMailService implements MailService {
    private final SesV2Client client;
    private final String fromEmail;

    public SesMailService(
            @Value("${app.mail.from-email:}") String fromEmail,
            @Value("${app.mail.region:${AWS_REGION:us-east-1}}") String region) {
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("APP_MAIL_FROM_EMAIL is required when mail delivery is enabled");
        }
        this.fromEmail = fromEmail.trim();
        this.client = SesV2Client.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Override
    public boolean isDeliveryEnabled() {
        return true;
    }

    @Override
    public void sendPasswordReset(String toEmail, String resetLink) {
        send(toEmail, "TaskHub password reset",
                "Use this one-time link to reset your TaskHub password. It expires in 1 hour:\n\n"
                        + resetLink + "\n\nIf you did not request this, ignore this email.");
    }

    @Override
    public void sendEmailVerification(String toEmail, String verifyLink) {
        send(toEmail, "Verify your TaskHub email",
                "Use this one-time link to verify your TaskHub email. It expires in 24 hours:\n\n"
                        + verifyLink + "\n\nIf you did not create this account, ignore this email.");
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
        try {
            client.sendEmail(request -> request
                    .fromEmailAddress(fromEmail)
                    .destination(Destination.builder().toAddresses(toEmail).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder().data(subject).charset("UTF-8").build())
                                    .body(Body.builder()
                                            .text(Content.builder().data(text).charset("UTF-8").build())
                                            .build())
                                    .build())
                            .build()));
        } catch (SesV2Exception ex) {
            throw TaskHubException.internalError("Security email delivery failed");
        }
    }

    @PreDestroy
    void close() {
        client.close();
    }
}
