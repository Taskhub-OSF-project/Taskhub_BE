package com.taskhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TaskHubApplication {
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    public static void main(String[] args) {
        SpringApplication.run(TaskHubApplication.class, args);
    }
}
