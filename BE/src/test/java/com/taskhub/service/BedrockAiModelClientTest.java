package com.taskhub.service;

import com.taskhub.config.BedrockProperties;
import com.taskhub.exception.TaskHubException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BedrockAiModelClientTest {

    @Test
    void sendsSystemPromptAndConfiguredModelThroughConverse() {
        BedrockProperties properties = new BedrockProperties();
        properties.setModelId("global.anthropic.claude-haiku-4-5-20251001-v1:0");

        Message assistantMessage = Message.builder()
                .role("assistant")
                .content(ContentBlock.fromText("{\"ok\":true}"))
                .build();
        ConverseResponse response = ConverseResponse.builder()
                .output(ConverseOutput.builder().message(assistantMessage).build())
                .build();
        AtomicReference<ConverseRequest> captured = new AtomicReference<>();
        BedrockRuntimeClient runtime = (BedrockRuntimeClient) Proxy.newProxyInstance(
                BedrockRuntimeClient.class.getClassLoader(),
                new Class<?>[]{BedrockRuntimeClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("converse")) {
                        captured.set((ConverseRequest) args[0]);
                        return response;
                    }
                    return null;
                });
        BedrockAiModelClient client = new BedrockAiModelClient(runtime, properties);

        assertEquals("{\"ok\":true}", client.generate("MODE = \"TIEU_CHI\"", 0.3f, 2048));

        ConverseRequest request = captured.get();
        assertEquals(properties.getModelId(), request.modelId());
        assertEquals(0.3f, request.inferenceConfig().temperature());
        assertEquals(2048, request.inferenceConfig().maxTokens());
        assertFalse(request.system().isEmpty());
        assertEquals("MODE = \"TIEU_CHI\"", request.messages().get(0).content().get(0).text());
    }

    @Test
    void rejectsCallsWhenBedrockIsDisabled() {
        BedrockProperties properties = new BedrockProperties();
        properties.setEnabled(false);
        BedrockAiModelClient client = new BedrockAiModelClient(null, properties);

        assertThrows(TaskHubException.class, () -> client.generate("hello"));
    }

    @Test
    void sendsImageAndPromptInTheSameConverseMessage() {
        BedrockProperties properties = new BedrockProperties();
        Message assistantMessage = Message.builder()
                .role("assistant")
                .content(ContentBlock.fromText("{\"criteria\":[]}"))
                .build();
        ConverseResponse response = ConverseResponse.builder()
                .output(ConverseOutput.builder().message(assistantMessage).build())
                .build();
        AtomicReference<ConverseRequest> captured = new AtomicReference<>();
        BedrockRuntimeClient runtime = (BedrockRuntimeClient) Proxy.newProxyInstance(
                BedrockRuntimeClient.class.getClassLoader(),
                new Class<?>[]{BedrockRuntimeClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("converse")) {
                        captured.set((ConverseRequest) args[0]);
                        return response;
                    }
                    return null;
                });

        BedrockAiModelClient client = new BedrockAiModelClient(runtime, properties);
        client.generateWithImage("read this brief", new byte[]{1, 2, 3}, "png", 0.1f, 1024);

        var content = captured.get().messages().get(0).content();
        assertEquals(ContentBlock.Type.IMAGE, content.get(0).type());
        assertEquals("png", content.get(0).image().formatAsString());
        assertEquals("read this brief", content.get(1).text());
    }
}
