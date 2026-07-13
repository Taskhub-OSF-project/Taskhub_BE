package com.taskhub.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class DotenvConfig {
    static {
        String baseDir = System.getProperty("user.dir");
        Path envPath = Path.of(baseDir, ".env");

        // Nếu .env không có ở thư mục hiện tại, thử BE/
        if (!envPath.toFile().exists()) {
            envPath = Path.of(baseDir, "BE", ".env");
        }

        Dotenv dotenv = Dotenv.configure()
                .directory(envPath.getParent().toString())
                .filename(".env")
                .ignoreIfMalformed()
                .load();
        dotenv.entries().forEach(e ->
                System.setProperty(e.getKey(), e.getValue())
        );
    }
}
