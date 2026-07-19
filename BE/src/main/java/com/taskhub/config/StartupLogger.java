package com.taskhub.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class StartupLogger {

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String baseUrl = "http://localhost:8080";

        log.info("TaskHub Backend ready at {}", baseUrl);
    }
}
