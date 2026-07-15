package com.taskhub.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.bedrock")
public class BedrockProperties {
    private boolean enabled = true;
    private String region = "us-east-1";
    private String modelId = "global.anthropic.claude-haiku-4-5-20251001-v1:0";
    private float temperature = 0.3f;
    private int maxTokens = 4096;

    @PostConstruct
    void validate() {
        if (!enabled) return;
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("APP_BEDROCK_REGION is required when Bedrock is enabled");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalStateException("APP_BEDROCK_MODEL_ID is required when Bedrock is enabled");
        }
        if (!Float.isFinite(temperature) || temperature < 0 || temperature > 1) {
            throw new IllegalStateException("APP_BEDROCK_TEMPERATURE must be between 0 and 1");
        }
        if (maxTokens < 1 || maxTokens > 8192) {
            throw new IllegalStateException("APP_BEDROCK_MAX_TOKENS must be between 1 and 8192");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public float getTemperature() { return temperature; }
    public void setTemperature(float temperature) { this.temperature = temperature; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
}
