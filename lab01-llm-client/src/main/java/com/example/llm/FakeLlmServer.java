package com.example.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import lombok.Data;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FakeLlmServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SSE_SEP = "\n\n";

    private static final String SSE_PREFIX = "data: ";

    private final HttpServer server;

    public int port;

    public FakeLlmServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            // ===== 第 1 步:读请求体(读成字符串) =====
            String reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("收到请求: " + reqBody);

            // ===== 场景开关:按请求体里的关键词决定演哪个剧本 =====
            if (reqBody.contains("TRIGGER_401")) {
                // 场景 b(401 错误)
                String json401 = """
                        {"error":{"message":"invalid api key"}}
                    """;
                byte[] bytes = json401.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                //   提示:状态码 401,body 为 {"error":{"message":"invalid api key"}}
                //   套路和场景 a 一样:字节数组 → sendResponseHeaders → write → close
                return; // 占位:写完场景 b 后删掉这行
            }
            if (reqBody.contains("TRIGGER_STREAM")) {
                // 场景 c(SSE 流式):
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    SseItem sseItem = new SseItem();
                    sseItem.choices = List.of(new Choice(new Delta("你")));
                    sendSse(os, sseItem);

                    sseItem = new SseItem();
                    sseItem.choices = List.of(new Choice(new Delta("好")));
                    sendSse(os, sseItem);

                    sseItem = new SseItem();
                    sseItem.choices = List.of(new Choice(new Delta(null), "stop"));
                    sendSse(os, sseItem);

                    sendSseDone(os);
                }
                return;
            }

            // ===== 场景 a(正常响应):标准 chat.completion JSON =====
            // 结构对齐 Day 2 真实报文的最小骨架:chat() 只解析 choices[0].message.content
            String okJson = """
                    {
                      "id": "chatcmpl-fake",
                      "object": "chat.completion",
                      "model": "fake-model",
                      "choices": [
                        {
                          "index": 0,
                          "message": {"role": "assistant", "content": "你好,我是假模型"},
                          "finish_reason": "stop"
                        }
                      ]
                    }
                    """;
            byte[] bytes = okJson.getBytes(StandardCharsets.UTF_8);

            // ===== 第 2 步:先写响应头(状态码 + body 字节数),再写 body =====
            //   声明多少字节就必须写满多少字节,所以是 bytes.length,不是随便一个 1024
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            // ===== 第 3 步:close —— try-with-resources 里自动关;不关客户端会一直等 =====
        });
    }

    /**
     * 发送一条sse消息，自动加前缀和分隔符
     * @param os
     * @param data
     * @throws Exception
     */
    private void sendSse(OutputStream os, Object data){
        try {
            String json = MAPPER.writeValueAsString(data);
            String line = String.join("", SSE_PREFIX, json, SSE_SEP);
            os.write(line.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 发送 [DONE] 结束标记
     * @param os
     * @throws Exception
     */
    private void sendSseDone(OutputStream os){
        try {
            String line = String.join("", SSE_PREFIX, "[DONE]", SSE_SEP);
            os.write(line.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void start(){
        server.start();
        port = server.getAddress().getPort();
    }

    public void stop(){
        server.stop(0);
    }


    @Data
    static class SseItem {
        private List<Choice> choices;
    }

    @Data
    static class Choice {
        private Delta delta;
        private String finish_reason;
        public Choice(Delta delta) {
            this.delta = delta;
        }
        public Choice(Delta delta, String finishReason) {
            this.delta = delta;
            this.finish_reason = finishReason;
        }
    }
    @Data
    static class Delta {
        private String content;
        public Delta(String content){
            this.content = content;
        }
    }

}
