package com.taskhub.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
            Twilio.init(accountSid, authToken);
            Message.creator(new PhoneNumber(normalizePhone(phone)),
                    new PhoneNumber(twilioPhoneNumber), message).create();
            log.info("Security SMS delivered to {}", maskPhone(phone));
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
}
