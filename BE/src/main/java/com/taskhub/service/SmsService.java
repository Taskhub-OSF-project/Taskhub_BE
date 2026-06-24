package com.taskhub.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsService {

    @Value("${app.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.phone-number:}")
    private String twilioPhoneNumber;

    @Async
    public void sendOtp(String phone, String code) {
        String message = String.format(
                "Ma xac thuc TaskHub cua ban: %s. Ma co hieu luc trong 5 phut.",
                code
        );
        sendSms(phone, message);
    }

    @Async
    public void sendOtpRecovery(String phone, String code) {
        String message = String.format(
                "Ma khoi phuc tai khoan TaskHub: %s. Ma co hieu luc trong 5 phut.",
                code
        );
        sendSms(phone, message);
    }

    private void sendSms(String phone, String message) {
        if (!smsEnabled) {
            log.info("[SMS MOCK] smsEnabled=false — would send to: {}, message: {}", phone, message);
            return;
        }
        if (accountSid == null || authToken == null || twilioPhoneNumber == null
                || accountSid.isBlank() || authToken.isBlank() || twilioPhoneNumber.isBlank()) {
            log.warn("[SMS] Twilio not configured — falling back to mock. Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_PHONE_NUMBER.");
            log.info("[SMS MOCK] to: {}, message: {}", phone, message);
            return;
        }
        try {
            Twilio.init(accountSid, authToken);
            Message twilioMessage = Message.creator(
                    new PhoneNumber(normalizePhone(phone)),
                    new PhoneNumber(twilioPhoneNumber),
                    message
            ).create();
            log.info("SMS sent to {}: SID={}", phone, twilioMessage.getSid());
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", phone, e.getMessage(), e);
        }
    }

    private String normalizePhone(String phone) {
        String trimmed = phone.trim();
        if (trimmed.startsWith("0")) {
            return "+84" + trimmed.substring(1);
        }
        if (!trimmed.startsWith("+")) {
            return "+" + trimmed;
        }
        return trimmed;
    }
}
