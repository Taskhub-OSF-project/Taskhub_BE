package com.taskhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@taskhub.local}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Async
    public void sendWelcomeEmail(String toEmail, String fullName) {
        String subject = "Chào mừng bạn đến với TaskHub! 🎓";
        String body = buildWelcomeEmail(fullName);
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendTaskAssignedEmail(String toEmail, String studentName, String taskTitle, Long taskId) {
        String subject = "Bạn đã được nhận công việc: " + taskTitle;
        String body = buildTaskAssignedEmail(studentName, taskTitle, taskId);
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendApplicationReceivedEmail(String toEmail, String hirerName, String studentName, String taskTitle, Long taskId) {
        String subject = studentName + " đã ứng tuyển công việc của bạn";
        String body = buildApplicationReceivedEmail(hirerName, studentName, taskTitle, taskId);
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendApplicationAcceptedEmail(String toEmail, String studentName, String taskTitle, Long taskId) {
        String subject = "Chúc mừng! Bạn đã được nhận cho: " + taskTitle;
        String body = buildApplicationAcceptedEmail(studentName, taskTitle, taskId);
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendSubmissionReceivedEmail(String toEmail, String hirerName, String taskTitle, Long taskId) {
        String subject = "Sinh viên đã nộp bài cho: " + taskTitle;
        String body = buildSubmissionReceivedEmail(hirerName, taskTitle, taskId);
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendRevisionRequestedEmail(String toEmail, String studentName, String taskTitle, Long taskId) {
        String subject = "Yêu cầu chỉnh sửa: " + taskTitle;
        String body = buildRevisionRequestedEmail(studentName, taskTitle, taskId);
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendTaskCompletedEmail(String toEmail, String studentName, String taskTitle, Long taskId) {
        String subject = "Công việc hoàn thành: " + taskTitle;
        String body = buildTaskCompletedEmail(studentName, taskTitle, taskId);
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendDisputeOpenedEmail(String toEmail, String studentName, String taskTitle, Long taskId) {
        String subject = "Khiếu nại đã được mở cho: " + taskTitle;
        String body = buildDisputeOpenedEmail(studentName, taskTitle, taskId);
        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendPaymentReceivedEmail(String toEmail, String studentName, String taskTitle, String amount, Long taskId) {
        String subject = "Bạn đã nhận thanh toán cho: " + taskTitle;
        String body = buildPaymentReceivedEmail(studentName, taskTitle, amount, taskId);
        sendEmail(toEmail, subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        if (!emailEnabled) {
            log.info("[EMAIL MOCK] To: {}, Subject: {}", to, subject);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private String buildWelcomeEmail(String name) {
        return String.format("""
            Xin chào %s!

            Chào mừng bạn đến với TaskHub - nền tảng freelance dành cho sinh viên!

            Bạn có thể bắt đầu bằng cách:
            • Tìm kiếm công việc phù hợp với kỹ năng
            • Tạo hồ sơ cá nhân để thu hút nhà tuyển dụng
            • Ứng tuyển các công việc thú vị

            Chúc bạn thành công!
            Đội ngũ TaskHub
            """, name);
    }

    private String buildTaskAssignedEmail(String name, String taskTitle, Long taskId) {
        return String.format("""
            Xin chào %s!

            Bạn đã được nhận công việc: "%s"

            Vui lòng đăng nhập để bắt đầu làm việc.
            """, name, taskTitle) + buildTaskLink(taskId);
    }

    private String buildApplicationReceivedEmail(String hirerName, String studentName, String taskTitle, Long taskId) {
        return String.format("""
            Xin chào %s!

            %s đã ứng tuyển công việc "%s" của bạn.

            Đăng nhập để xem hồ sơ và chấp nhận.
            """, hirerName, studentName, taskTitle) + buildTaskLink(taskId);
    }

    private String buildApplicationAcceptedEmail(String studentName, String taskTitle, Long taskId) {
        return String.format("""
            Xin chào %s!

            Chúc mừng! Đơn ứng tuyển của bạn cho "%s" đã được chấp nhận.

            Đăng nhập để bắt đầu làm việc.
            """, studentName, taskTitle) + buildTaskLink(taskId);
    }

    private String buildSubmissionReceivedEmail(String hirerName, String taskTitle, Long taskId) {
        return String.format("""
            Xin chào %s!

            Sinh viên đã nộp bài cho công việc "%s".

            Vui lòng đăng nhập để kiểm tra và phê duyệt.
            """, hirerName, taskTitle) + buildTaskLink(taskId);
    }

    private String buildRevisionRequestedEmail(String studentName, String taskTitle, Long taskId) {
        return String.format("""
            Xin chào %s!

            Nhà tuyển dụng đã yêu cầu chỉnh sửa cho công việc "%s".

            Vui lòng đăng nhập để xem chi tiết và tiếp tục làm việc.
            """, studentName, taskTitle) + buildTaskLink(taskId);
    }

    private String buildTaskCompletedEmail(String studentName, String taskTitle, Long taskId) {
        return String.format("""
            Xin chào %s!

            Công việc "%s" đã được hoàn thành và thanh toán đã được giải ngân.

            Cảm ơn bạn đã làm việc trên TaskHub!
            """, studentName, taskTitle);
    }

    private String buildDisputeOpenedEmail(String studentName, String taskTitle, Long taskId) {
        return String.format("""
            Xin chào %s!

            Một khiếu nại đã được mở cho công việc "%s".

            Vui lòng đăng nhập để xem chi tiết và phản hồi.
            """, studentName, taskTitle) + buildTaskLink(taskId);
    }

    private String buildPaymentReceivedEmail(String studentName, String taskTitle, String amount, Long taskId) {
        return String.format("""
            Xin chào %s!

            Bạn đã nhận được thanh toán %s VNĐ cho công việc "%s".

            Tiền đã được cộng vào ví TaskHub của bạn.
            """, studentName, amount, taskTitle);
    }

    private String buildTaskLink(Long taskId) {
        return "\n\nTruy cập ngay: " + baseUrl + "/hirer/tasks/" + taskId + "\n";
    }
}
