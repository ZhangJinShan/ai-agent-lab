package com.example.llm;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LlmClientTest {

    static FakeLlmServer fake;

    static LlmClient llmClient;


    @BeforeAll
    static void setUp() throws Exception{
        fake = new FakeLlmServer();
        fake.start();
        // LlmClient 签名是 (baseUrl, model, apiKey) —— 注意参数顺序!
        llmClient = new LlmClient(
                "http://localhost:" + fake.port + "/v1",   // baseUrl 指向假服务器!
                "fake-model",                               // model
                "test-key");                                // apiKey,假服务器不校验,非空即可
    }

    @AfterAll    // 整个测试类跑完:必须停掉,否则端口被占,IDEA 里跑第二次就撞
    static void tearDown() {
        fake.stop();
    }

    @Test
    void 测试1_正常响应解析出content() {
        // 走场景 a(不带 TRIGGER 关键词),假服务器返回 content="你好,我是假模型"
        String answer = llmClient.chat("你是测试助手", "hello");
        // JUnit 断言:期望 == 实际;不等则测试红,并打印两边差异
        //   (不要用 Java 的 assert 关键字:默认不生效,失败信息也不友好)
        assertEquals("你好,我是假模型", answer);
    }

    @Test
    void 测试2_错误响应抛出带原文的异常() {
        // 走场景 b(消息里带 TRIGGER_401),假服务器返回 HTTP 401 + {"error":{"message":"invalid api key"}}
        // assertThrows 语义:「这段代码必须抛 RuntimeException」
        //   抛了 → 把异常对象返回给我们继续检查内容
        //   没抛 → 测试红(说明错误处理被改丢了)
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> llmClient.chat("你是测试助手", "TRIGGER_401"));
        // 错误处理的质量标准:异常 message 里必须同时有状态码和服务端原文
        assertTrue(ex.getMessage().contains("401"), "异常信息应包含状态码 401,实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("invalid api key"), "异常信息应包含服务端原文,实际: " + ex.getMessage());
    }

    @Test
    void 测试3_流式拼接且跳过无content的chunk() {
        // 调 chatStream() 时用户消息里带 "TRIGGER_STREAM",走场景 c
        StringBuilder sb = new StringBuilder();
        llmClient.chatStream("你是测试助手", "TRIGGER_STREAM", sb::append);
        assertEquals("你好", sb.toString());

        // TODO(你自己写): 用 StringBuilder::append 当 onToken,拼接结果应 == "你好"
        //       —— 若首尾 chunk 没被跳过,这里会 NPE 或多出 null 字符串
    }
}
