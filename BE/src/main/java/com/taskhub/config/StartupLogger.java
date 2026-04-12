package com.taskhub.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupLogger {

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String baseUrl = "http://localhost:8080";

        System.out.println();
        System.out.println("========================================");
        System.out.println("TaskHub Backend ready");
        System.out.println("➜  Local:   " + baseUrl + "/");
        System.out.println("➜  Swagger: " + baseUrl + "/swagger-ui.html");
        System.out.println("========================================");
        System.out.println();
    }
}
