package com.taskhub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
@Getter @Setter
public class RateLimitProperties {
    /** Max login attempts per IP per minute. */
    private int loginPerMinute = 10;
    /** Max forgot-password requests per IP per hour. */
    private int forgotPasswordPerHour = 5;
    /** Max refresh requests per IP per minute. */
    private int refreshPerMinute = 30;
    /** Max registration attempts per IP per hour. */
    private int registerPerHour = 10;
    /** Max account-recovery operations per IP per hour. */
    private int recoveryPerHour = 10;
    /** Max authenticated AI operations per IP per minute. */
    private int aiPerMinute = 30;
    /** Max public chatbot calls per IP per minute. */
    private int publicAiPerMinute = 10;
}
