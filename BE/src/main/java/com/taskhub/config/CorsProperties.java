package com.taskhub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.cors")
@Getter @Setter
public class CorsProperties {
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173", "http://localhost:3000"));
}
