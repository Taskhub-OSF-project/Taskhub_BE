package com.taskhub.lambda;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.TaskHubApplication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

public class StreamLambdaHandler implements RequestStreamHandler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static volatile SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;
    private static final Set<String> ALLOWED_ORIGINS = Arrays.stream(
                    System.getenv().getOrDefault("APP_CORS_ALLOWED_ORIGINS", "http://localhost:5173").split(","))
            .map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        JsonNode event = OBJECT_MAPPER.readTree(inputStream);
        if (isWarmupEvent(event)) {
            getHandler();
            OBJECT_MAPPER.writeValue(outputStream, Map.of("statusCode", 200, "body", "warm"));
            outputStream.flush();
            return;
        }

        AwsProxyRequest request = OBJECT_MAPPER.treeToValue(event, AwsProxyRequest.class);
        normalizeApiGatewayStagePath(request);

        AwsProxyResponse response = isCorsPreflight(request)
                ? corsPreflightResponse()
                : getHandler().proxy(request, context);

        addCorsHeaders(response, request);
        OBJECT_MAPPER.writeValue(outputStream, response);
        outputStream.flush();
    }

    private boolean isWarmupEvent(JsonNode event) {
        String source = text(event, "source");
        String action = text(event, "action");
        return "taskhub.warmer".equals(source)
                || ("aws.events".equals(source) && "Scheduled Event".equals(text(event, "detail-type")))
                || "keep-warm".equals(action);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> getHandler() {
        if (handler == null) {
            synchronized (StreamLambdaHandler.class) {
                if (handler == null) {
                    try {
                        handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(TaskHubApplication.class);
                    } catch (ContainerInitializationException ex) {
                        throw new ExceptionInInitializerError(ex);
                    }
                }
            }
        }

        return handler;
    }

    private void normalizeApiGatewayStagePath(AwsProxyRequest request) {
        String normalizedPath = withoutProdStage(request.getPath());
        request.setPath(normalizedPath);

        if (request.getRequestContext() != null) {
            request.getRequestContext().setPath(normalizedPath);
        }
    }

    private String withoutProdStage(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }

        if (path.equals("/Prod")) {
            return "/";
        }

        if (path.startsWith("/Prod/")) {
            return path.substring("/Prod".length());
        }

        return path.startsWith("/") ? path : "/" + path;
    }

    private boolean isCorsPreflight(AwsProxyRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getHttpMethod());
    }

    private AwsProxyResponse corsPreflightResponse() {
        AwsProxyResponse response = new AwsProxyResponse();
        response.setStatusCode(204);
        response.setBody("");
        return response;
    }

    private void addCorsHeaders(AwsProxyResponse response, AwsProxyRequest request) {
        Map<String, String> headers = response.getHeaders() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(response.getHeaders());
        removeCorsHeaders(headers);
        headers.putAll(corsHeaders(request));
        response.setHeaders(headers);

        if (response.getMultiValueHeaders() != null) {
            removeCorsHeaders(response.getMultiValueHeaders());
        }
    }

    private Map<String, String> corsHeaders(AwsProxyRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        String origin = headerValue(request, "Origin");
        if (isOriginAllowed(origin)) {
            headers.put("Access-Control-Allow-Origin", origin);
            headers.put("Access-Control-Allow-Credentials", "true");
        }
        headers.put("Vary", "Origin");
        headers.put("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Requested-With,Accept,Origin");
        headers.put("Access-Control-Max-Age", "86400");
        return headers;
    }

    static boolean isOriginAllowed(String origin) {
        return origin != null && ALLOWED_ORIGINS.contains(origin.trim());
    }

    private String headerValue(AwsProxyRequest request, String name) {
        if (request.getHeaders() == null) {
            return null;
        }

        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private void removeCorsHeaders(Map<String, ?> headers) {
        headers.keySet().removeIf(key -> key != null && key.toLowerCase().startsWith("access-control-"));
    }
}
