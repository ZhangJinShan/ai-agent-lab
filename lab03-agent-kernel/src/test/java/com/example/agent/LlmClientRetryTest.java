package com.example.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 500 重试成功 / 401 不重试 / 网络错误重试到耗尽。
 * 断言不看日志,看假服务器的「请求次数」—— 重不重试,数字说了算
 */
public class LlmClientRetryTest {

    FakeLlmServer fake;

    LlmClient llmClient;

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeLlmServer();
        fake.start();
        llmClient = new LlmClient(
                "http://localhost:" + fake.port,
                "fake-model",
                "test-key",
                new AgentConfig.Builder().systemPrompt("你是测试助手").build(),
                // 5ms 基准退避:测试里别真睡 1s+2s
                new RetryExecutor(3, 5));
    }

    @AfterEach
    void tearDown() {
        fake.stop();
    }

    @Test
    void 测试1_前两次500第三次成功_自动重试拿到结果() {
        String answer = llmClient.chat("你是测试助手", "TRIGGER_500_TWICE");
        assertEquals("你好,我是假模型", answer);
        assertEquals(3, fake.requestCount(), "前两次 500,应该重试到第 3 次才成功");
    }

    @Test
    void 测试2_401鉴权失败_一次都不重试() {
        LlmException ex = assertThrows(LlmException.class,
                () -> llmClient.chat("你是测试助手", "TRIGGER_401"));
        assertEquals(401, ex.getStatusCode(), "状态码应该在类型字段里,不是拼在 message 里");
        assertTrue(ex.getMessage().contains("invalid api key"), "异常信息应包含服务端原文");
        assertEquals(1, fake.requestCount(), "401 不该重试,只发 1 次请求");
    }

    @Test
    void 测试3_端口不通_按网络错误重试到耗尽() throws Exception {
        // 把 baseUrl 指到不存在的端口:连接被拒 → 网络类错误 → 可重试
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort(); // 借一个空闲端口立刻还掉,连它必被拒
        }
        RetryExecutor retry = new RetryExecutor(3, 5);
        LlmClient dead = new LlmClient(
                "http://localhost:" + deadPort,
                "fake-model",
                "test-key",
                new AgentConfig.Builder().systemPrompt("你是测试助手").build(),
                retry);

        assertThrows(RuntimeException.class, () -> dead.chat("你是测试助手", "hello"));
        assertEquals(3, retry.getLastAttemptCount(), "网络错误应重试到次数耗尽");
        assertEquals(0, fake.requestCount(), "请求根本没到假服务器");
    }
}
