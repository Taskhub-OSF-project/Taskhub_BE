package com.taskhub.service;

public interface AiModelClient {
    String generate(String prompt);
    String generate(String prompt, float temperature, int maxTokens);
}
