package com.example.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhangjinshan
 * @description: Llm Invoke Client
 * @create: 2026/10/11
 **/
public class LlmClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    private final String baseUrl;

    private final String model;

    private final String apiKey;

    public LlmClient(String baseUrl, String model, String apiKey) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl 不能为空");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey 不能为空");
        }

        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
    }

    public String chat(String systemPrompt, String userMessage){
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        ));
        try {
            String jsonBody = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)      // Bearer 后面有空格!
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                String err = response.body();
                if (err.length() > 500) err = err.substring(0, 500);
                throw new RuntimeException("LLM 调用失败,HTTP " + response.statusCode() + ": " + err);
            }
            JsonNode root = mapper.readTree(response.body());
            String content = root.path("choices").path(0)
                    .path("message").path("content").asText();
            if (content.isEmpty()) {
                throw new RuntimeException("响应里没有 content,原始响应: " + response.body());
            }
            return content;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
