package com.taskhub.lambda;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.TaskHubApplication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class StreamLambdaHandler implements RequestStreamHandler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static volatile SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        AwsProxyRequest request = OBJECT_MAPPER.readValue(inputStream, AwsProxyRequest.class);
        normalizeApiGatewayStagePath(request);

        AwsProxyResponse response = isCorsPreflight(request)
                ? corsPreflightResponse()
                : getHandler().proxy(request, context);

        addCorsHeaders(response, request);
        OBJECT_MAPPER.writeValue(outputStream, response);
        outputStream.flush();
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
        headers.put("Access-Control-Allow-Origin", allowedOrigin(request));
        headers.put("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Requested-With,Accept,Origin");
        headers.put("Access-Control-Max-Age", "86400");
        return headers;
    }

    private String allowedOrigin(AwsProxyRequest request) {
        String origin = headerValue(request, "Origin");
        return origin == null || origin.isBlank() ? "*" : origin;
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
