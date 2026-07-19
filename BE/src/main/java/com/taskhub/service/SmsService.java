package com.taskhub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Service
@Slf4j
public class SmsService {
    private static final String TWILIO_MESSAGES_URL =
            "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    private final boolean smsEnabled;
    private final String accountSid;
    private final String authToken;
    private final String twilioPhoneNumber;
    private final HttpClient httpClient;

    @Autowired
    public SmsService(
            @Value("${app.sms.enabled:false}") boolean smsEnabled,
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${twilio.phone-number:}") String twilioPhoneNumber) {
        this(smsEnabled, accountSid, authToken, twilioPhoneNumber,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    SmsService(boolean smsEnabled, String accountSid, String authToken,
               String twilioPhoneNumber, HttpClient httpClient) {
        this.smsEnabled = smsEnabled;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.twilioPhoneNumber = twilioPhoneNumber;
        this.httpClient = httpClient;
    }

    public boolean isDeliveryEnabled() {
        return smsEnabled && notBlank(accountSid) && notBlank(authToken) && notBlank(twilioPhoneNumber);
    }

    public void sendOtp(String phone, String code) {
        sendSms(phone, "Ma xac thuc TaskHub cua ban: " + code + ". Ma co hieu luc trong 5 phut.");
    }

    public void sendOtpRecovery(String phone, String code) {
        sendSms(phone, "Ma khoi phuc tai khoan TaskHub: " + code + ". Ma co hieu luc trong 5 phut.");
    }

    private void sendSms(String phone, String message) {
        if (!isDeliveryEnabled()) {
            throw new IllegalStateException("SMS delivery is disabled or incomplete");
        }
        try {
            String form = "To=" + encode(normalizePhone(phone))
                    + "&From=" + encode(twilioPhoneNumber)
                    + "&Body=" + encode(message);
            String credentials = Base64.getEncoder().encodeToString(
                    (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TWILIO_MESSAGES_URL.formatted(accountSid)))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + credentials)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Twilio returned HTTP " + response.statusCode());
            }
            log.info("Security SMS delivered to {}", maskPhone(phone));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Security SMS delivery interrupted for {}", maskPhone(phone));
            throw new IllegalStateException("SMS delivery interrupted", ex);
        } catch (Exception ex) {
            log.error("Security SMS delivery failed for {}", maskPhone(phone));
            throw new IllegalStateException("SMS delivery failed", ex);
        }
    }

    private String normalizePhone(String phone) {
        String trimmed = phone.trim();
        if (trimmed.startsWith("0")) return "+84" + trimmed.substring(1);
        return trimmed.startsWith("+") ? trimmed : "+" + trimmed;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 3);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
