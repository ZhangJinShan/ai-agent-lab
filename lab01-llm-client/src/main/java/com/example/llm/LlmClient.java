package com.example.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        Map<String, Object> body = buildBody(systemPrompt, userMessage, false);
        try {
            HttpRequest request = getHttpRequest(body);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                throwLlmError(response.statusCode(), response.body());
            }
            String content = extractChoiceContent(mapper.readTree(response.body()), "message");
            if (content == null || content.isEmpty()) {
                throw new RuntimeException("响应里没有 content,原始响应: " + response.body());
            }
            return content;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void chatStream(String systemPrompt, String userMessage, Consumer<String> onToken){
        Map<String, Object> body = buildBody(systemPrompt, userMessage, true);

        try {
            HttpRequest request = getHttpRequest(body);

            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() != 200) {
                // 注意!出错时响应体不是 SSE,是普通 JSON 错误,得先把行拼回来看
                throwLlmError(response.statusCode(), response.body().collect(Collectors.joining()));
            }
            boolean done = false;
            String prefix = "data: ";
            Iterable<String> lines = () -> response.body().iterator();
            for (String line : lines) {
                if (done || !line.startsWith(prefix)) {
                    continue;
                }
                String payload = line.substring(prefix.length()).trim();
                if (payload.equals("[DONE]")) {
                    done = true;
                    continue;
                }
                String token = extractChoiceContent(mapper.readTree(payload), "delta");
                if (token == null || token.trim().isBlank()) {
                    continue; // 首尾 chunk / 思考过程在这里被跳过
                }
                onToken.accept(token.trim());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> buildBody(String systemPrompt, String userMessage, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        ));
        if (stream) {
            body.put("stream", true); // 表示流式输出
        }
        return body;
    }

    /** 从 choices[0].<container>.content 取文本;取不到返回 null。非流式 container 是 message,流式是 delta */
    private String extractChoiceContent(JsonNode root, String container) {
        JsonNode contentNode = root.path("choices").path(0).path(container).path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            return null;
        }
        return contentNode.asText();
    }

    /** 统一的非 200 错误处理:errBody 超长只保留前 500 字符 */
    private void throwLlmError(int statusCode, String errBody) {
        throw new RuntimeException("LLM 调用失败,HTTP " + statusCode + ": " + errBody.substring(0, Math.min(errBody.length(), 500)));
    }

    private HttpRequest getHttpRequest(Map<String, Object> body) throws JsonProcessingException {
        String jsonBody = mapper.writeValueAsString(body);

        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)      // Bearer 后面有空格!
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }
}
