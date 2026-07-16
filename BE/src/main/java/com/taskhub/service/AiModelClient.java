package com.taskhub.service;

public interface AiModelClient {
    String generate(String prompt);
    String generate(String prompt, float temperature, int maxTokens);

    /**
     * Sends one image together with a text prompt to a multimodal model.
     * The format is one of: png, jpeg, gif, webp.
     */
    String generateWithImage(String prompt, byte[] imageBytes, String imageFormat,
                             float temperature, int maxTokens);
}
