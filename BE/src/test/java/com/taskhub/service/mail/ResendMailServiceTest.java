package com.taskhub.service.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.taskhub.exception.TaskHubException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResendMailServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;
    private URI endpoint;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"id\":\"email-test\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus.get(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/emails");
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void productionBean_usesTheConfiguredConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "resend-test",
                    Map.of(
                            "app.mail.delivery-enabled", "true",
                            "app.mail.provider", "resend",
                            "app.mail.resend.api-key", "resend-test-key",
                            "app.mail.from-email", "TaskHub <otp@mail.taskhubvn.com>")));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(ResendMailService.class);
            context.refresh();

            assertTrue(context.getBean(ResendMailService.class).isDeliveryEnabled());
        }
    }

    @Test
    void registrationOtp_sendsAuthenticatedJsonRequest() throws Exception {
        ResendMailService service = new ResendMailService(
                objectMapper,
                HttpClient.newHttpClient(),
                "resend-test-key",
                "TaskHub <otp@mail.taskhubvn.com>",
                endpoint);

        service.sendRegistrationOtp("student@example.com", "123456");

        assertEquals("Bearer resend-test-key", authorization.get());
        JsonNode payload = objectMapper.readTree(requestBody.get());
        assertEquals("TaskHub <otp@mail.taskhubvn.com>", payload.get("from").asText());
        assertEquals("student@example.com", payload.get("to").get(0).asText());
        assertTrue(payload.get("subject").asText().contains("đăng ký"));
        assertTrue(payload.get("text").asText().contains("123456"));
    }

    @Test
    void providerError_isReturnedAsSafeApplicationError() {
        responseStatus.set(422);
        ResendMailService service = new ResendMailService(
                objectMapper,
                HttpClient.newHttpClient(),
                "resend-test-key",
                "TaskHub <otp@mail.taskhubvn.com>",
                endpoint);

        TaskHubException error = assertThrows(TaskHubException.class,
                () -> service.sendLoginOtp("student@example.com", "654321"));

        assertEquals(500, error.getStatus().value());
        assertTrue(error.getMessage().contains("Không thể gửi mã bảo mật"));
    }

    @Test
    void missingApiKey_failsFastWithoutLeakingSecrets() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ResendMailService(
                        objectMapper,
                        HttpClient.newHttpClient(),
                        " ",
                        "TaskHub <otp@mail.taskhubvn.com>",
                        endpoint));

        assertTrue(error.getMessage().contains("APP_MAIL_RESEND_API_KEY"));
    }
}
