package com.example.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用假 LLM 服务器:按请求体里的关键词决定演哪个剧本。
 * 放在 test 目录 —— 它是测试道具,不是生产代码。
 */
public class FakeLlmServer {

    private final HttpServer server;

    /** 一共接收了多少个请求，用来断言[重试了几次/没重试] */
    private final AtomicInteger requestCount = new AtomicInteger();

    /** TRIGGER_500_TWICE 剧本的技计数器 */
    private final AtomicInteger trigger500Count = new AtomicInteger();

    public int port;

    static final long SILENCE_MS = 800;   // 测试用短静默;想演示体感时改成 15_000

    public FakeLlmServer() throws IOException{
        server = HttpServer.create(new InetSocketAddress(0), 0);
        // 注意:LlmClient 拼的路径是 baseUrl + "/chat/completions",没有 /v1 前缀
        server.createContext("/chat/completions", exchange -> {
            String reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int n = requestCount.incrementAndGet();
            System.out.println("假服务器收到第 " + n + " 个请求");

            // 场景 b(401):鉴权失败
            if (reqBody.contains("TRIGGER_401")) {
                respond(exchange, 401, "{\"error\":{\"message\":\"invalid api key\"}}");
                return;
            }

            // 场景 c(前两次 500,第三次成功):模拟服务端抖动
            if (reqBody.contains("TRIGGER_500_TWICE")) {
                if (trigger500Count.incrementAndGet() <= 2) {
                    respond(exchange, 500, "{\"error\":{\"message\":\"internal error\"}}");
                    return;
                }
                // 第 3 次起落到下面的正常响应
            }

            // 场景 d:先吐字、再沉默(TRIGGER_SLOW_STREAM)—— 触发「已吐字」分支
            if (reqBody.contains("TRIGGER_SLOW_STREAM")) {
                try (OutputStream os = startSse(exchange)){
                    writeSse(os, "你");          // 先发一个 chunk
                    Thread.sleep(SILENCE_MS);          // 沉默
                    writeSse(os, "好");          // 第二个 chunk 到达时,客户端才发现超时
                    writeSseEnd(os);
                    return;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            // 场景 e:首个 chunk 迟到(TRIGGER_LATE_FIRST_CHUNK)—— 触发「零输出」分支
            if (reqBody.contains("TRIGGER_LATE_FIRST_CHUNK")) {
                try (OutputStream os = startSse(exchange)) {
                    Thread.sleep(SILENCE_MS);          // 先沉默,一个字都没发
                    writeSse(os, "你");
                    writeSseEnd(os);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            // 场景 f:正常速度的流(TRIGGER_STREAM)—— 验证不误杀
            if (reqBody.contains("TRIGGER_STREAM")) {
                try (OutputStream os = startSse(exchange)) {
                    writeSse(os, "你");
                    writeSse(os, "好");
                    writeSseEnd(os);
                }
                return;
            }

            // 场景 a(正常响应):标准 chat.completion JSON
            respond(exchange, 200, """
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
                      """);
        });
    }

    /** 只负责发响应头(整个请求只许调一次),返回输出流 */
    private OutputStream startSse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);   // 0 = chunked 传输,流式场景的标准做法
        return exchange.getResponseBody();
    }

    /** 只负责写一个 chunk,不碰响应头 */
    private void writeSse(OutputStream os, String token) throws IOException {
        os.write(("data: {\"choices\":[{\"delta\":{\"content\":\"" + token + "\"}}]}\n\n")
                .getBytes(StandardCharsets.UTF_8));
        os.flush();   // 每个 chunk 立刻刷出去,不然客户端收不到「流」的效果
    }

    private void writeSseEnd(OutputStream os) throws IOException {
        os.write("data: [DONE]".getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    /**
     * 字节数组 → sendResponseHeaders → write → close
     * @param exchange
     * @param status
     * @param json
     * @throws IOException
     */
    private void respond(HttpExchange exchange, int status, String json) throws IOException{
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()){
            os.write(bytes);
        }
    }

    public int requestCount() {
        return requestCount.get();
    }

    public void start() {
        server.start();
        port = server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }
}
