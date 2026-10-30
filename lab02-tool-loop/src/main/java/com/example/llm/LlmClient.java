package com.example.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

            return parseChatResponse(response);
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
            for (String line : (Iterable<? extends String>) response.body() :: iterator) {
                if (done) {
                    break;
                }
                if (!line.startsWith(prefix)) {
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

    @JsonIgnoreProperties(ignoreUnknown = true) // LLM 偶尔会多输出字段,忽略掉而不是解析失败
    public record Resume(String name, int years, List<String> skills) {}

    public Resume extractJson(String systemPrompt, String userText) {
        // 1. 调 chat() 拿回复
        String reply = chat(systemPrompt, userText);
        try {
            // 2. 解析
            return parseResume(reply);
        } catch (Exception first) {
            // 3. 解析失败 → 把「你的输出不是合法 JSON,请重新只输出 JSON」追加进对话重试 1 次
            //    chat() 只支持单轮,这里把上一次的错误输出和提示拼进 userText 模拟多轮
            String retryReply = chat(systemPrompt,
                    userText + "\n\n你上一次的输出:\n" + reply + "\n你的输出不是合法 JSON,请重新只输出 JSON");
            try {
                return parseResume(retryReply);
            } catch (Exception second) {
                // 4. 再失败 → 抛异常,带上原始输出
                throw new RuntimeException("LLM 输出不是合法的 Resume JSON,原始输出: " + retryReply, second);
            }
        }
    }

    public Resume chatExtractJson(String systemPrompt, String userText){
        Map<String, Object> body = buildBody(systemPrompt, userText, false);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("max_tokens", 50);
        try {
            HttpRequest httpRequest = getHttpRequest(body);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            String content = parseChatResponse(response);
            return mapper.readValue(content, Resume.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 通用入口:任意 messages + 可选 tools 声明,返回原始响应 JSON 文本
     * @param messages
     * @param toolsJson
     * @return
     */
    public String chatCompletions(List<Map<String, Object>> messages, String toolsJson){
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            if (toolsJson != null && !toolsJson.isBlank()) {
                // 关键:toolsJson 是 JSON 字符串,必须 readTree 之后挂进 body,
                // 否则序列化时会被当成普通字符串转义,服务端收到的是带 \" 的天书
                body.put("tools", mapper.readTree(toolsJson));
            }
            HttpRequest httpRequest = getHttpRequest(body);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throwLlmError(response.statusCode(), response.body());
            }
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }

    }



    private String parseChatResponse(HttpResponse<String> response) throws JsonProcessingException {
        if (response.statusCode() != 200) {
            throwLlmError(response.statusCode(), response.body());
        }
        JsonNode root = mapper.readTree(response.body());
        String finishReason = root.path("choices").path(0).path("finish_reason").asText();
        if ("length".equals(finishReason)) {
            throw new RuntimeException("输出被 max_tokens 截断,JSON 不完整,请调大 max_tokens 或缩短输入");
        }
        String content = extractChoiceContent(root, "message");
        if (content == null || content.isEmpty()) {
            throw new RuntimeException("响应里没有 content,原始响应: " + response.body());
        }
        return content;
    }


    private Resume parseResume(String reply) throws JsonProcessingException {
        return mapper.treeToValue(mapper.readTree(reply), Resume.class);
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
                .timeout(Duration.ofSeconds(60)) // LLM 生成耗时长,10 秒不够用
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)      // Bearer 后面有空格!
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }
}
