package com.example.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author zhangjinshan
 * @description: Llm Stream Retry
 * @create: 2026/10/28
 **/
public class LlmClientTimeoutTest {

    FakeLlmServer fake;

    LlmClient llmClient;


    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeLlmServer();
        fake.start();
        // 静默超时 300ms,总超时给大(60s)—— 测的是静默超时,别让总超时抢戏
        AgentConfig config = AgentConfig.builder()
                .systemPrompt("你是测试助手")
                .silentTimeoutMs(300L)
                .requestTimeoutMs(60_000L)
                .build();
        llmClient = new LlmClient(
                "http://localhost:" + fake.port,
                "fake-model", "test-key", config,
                new RetryExecutor(3, 5),      // 非流式,5ms 退避
                new RetryExecutor(2, 5));     // 流式:最多 2 次,同样 5ms 退避
    }

    @AfterEach
    void tearDown() {
        fake.stop();
    }

    @Test
    void 测试1_已吐字后沉默_报错且不重试() {
        long start = System.nanoTime();
        AtomicReference<String> got = new AtomicReference<>("");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> llmClient.chatStream("你是测试助手", "TRIGGER_SLOW_STREAM",
                        token -> got.updateAndGet(s -> s + token)));

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(ex.getMessage().contains("静默超时"), "异常要说人话");
        // TODO: 断言「不重试」—— 用哪个数字?fake.requestCount() 应该是几?
        // TODO: 断言第一个字已经吐出去了 —— got.get() 应该是什么?
        // TODO: 断言「没干等」—— elapsedMs 应该小于多少?(想想整个剧本走完要多久)
    }

    @Test
    void 测试2_首字迟到没吐过字_安全重试到耗尽() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> llmClient.chatStream("你是测试助手", "TRIGGER_LATE_FIRST_CHUNK",
                        token -> fail("超时前不该吐出任何字")));

        assertTrue(ex.getMessage().contains("静默超时"));
        // TODO: 断言「重试了」—— fake.requestCount() 应该是几?(流式重试器最多 2 次)
    }

    @Test
    void 测试3_正常速度的流_不误杀拼接完整() {
        StringBuilder sb = new StringBuilder();
        llmClient.chatStream("你是测试助手", "TRIGGER_STREAM", sb::append);
        assertEquals("你好", sb.toString(), "TRIGGER_STREAM 测试完成");
    }

    @Test
    void 测试4_静默预算大于总预算_拒绝构建配置() {
        // 纯逻辑,不起服务器,秒跑
        // TODO: assertThrows IllegalArgumentException,
        //       silentTimeoutMs(70_000L) + requestTimeoutMs(60_000L) 构建 AgentConfig
    }
}
