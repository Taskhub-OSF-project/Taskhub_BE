package com.taskhub;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import jakarta.annotation.PostConstruct;
import java.net.ServerSocket;
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
        killPort8080();
        loadEnv();
        SpringApplication.run(TaskHubApplication.class, args);
    }

    private static void loadEnv() {
        try {
            String baseDir = System.getProperty("user.dir");
            Path envPath = Path.of(baseDir, ".env");
            if (!envPath.toFile().exists()) {
                envPath = Path.of(baseDir, "BE", ".env");
            }
            if (envPath.toFile().exists()) {
                Dotenv dotenv = Dotenv.configure()
                        .directory(envPath.getParent().toString())
                        .filename(".env")
                        .ignoreIfMalformed()
                        .load();
                dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
                System.out.println("[INFO] Loaded .env from " + envPath);
            }
        } catch (Exception e) {
            System.out.println("[WARN] Could not load .env file: " + e.getMessage());
        }
    }

    private static void killPort8080() {
        try {
            ServerSocket socket = new ServerSocket(8080);
            socket.close();
            return;
        } catch (Exception e) {
        }

        String os = System.getProperty("os.name").toLowerCase();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c",
                    "for /f \"tokens=5\" %a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do taskkill /PID %a /F");
            } else {
                pb = new ProcessBuilder("bash", "-c",
                    "lsof -ti:8080 | xargs -r kill -9");
            }
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("[WARN] Could not kill port 8080: " + e.getMessage());
        }
    }
}
