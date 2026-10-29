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

        System.out.println(ex.getMessage());

        assertTrue(ex.getMessage().contains("静默超时"), "异常要说人话");
        // T// 已吐字 → 抛普通 RuntimeException → 不可重试 → 只发了 1 次请求
        assertEquals(1, fake.requestCount(), "已兔子后超时，不重试");
        // 第一个 chunk 在超时检查之前就吐出去了,第二个「好」永远不会到达 onToken
        assertEquals("你", got.get(), "第一个字已经吐给调用方了");
        // 超时检查在「收到第 2 行时」触发(近似实现的盲区,README 有写):
        // 一轮剧本约 SILENCE_MS(800ms)就该断;若重试会 ≥2×800ms,若干等是 60s 总超时
        assertTrue(elapsedMs < FakeLlmServer.SILENCE_MS * 2,
                "没干等也没重试,实际耗时 " + elapsedMs + "ms");
    }

    @Test
    void 测试2_首字迟到没吐过字_安全重试到耗尽() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> llmClient.chatStream("你是测试助手", "TRIGGER_LATE_FIRST_CHUNK",
                        token -> fail("超时前不该吐出任何字")));

        assertTrue(ex.getMessage().contains("静默超时"));
        // 零输出 → LlmTimeoutException 可重试 → 流式重试器最多 2 次,耗尽后抛出
        assertEquals(2, fake.requestCount(), "1 次原始尝试 + 1 次重试,共 2 个请求");
    }

    @Test
    void 测试3_正常速度的流_不误杀拼接完整() {
        StringBuilder sb = new StringBuilder();
        llmClient.chatStream("你是测试助手", "TRIGGER_STREAM", sb::append);
        assertEquals("你好", sb.toString(), "TRIGGER_STREAM 测试完成");
    }

    @Test
    void 测试4_静默预算大于总预算_拒绝构建配置(){
            // 纯逻辑,不起服务器,秒跑
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> AgentConfig.builder()
                            .systemPrompt("你是测试助手")   // 必须设!不然先撞 systemPrompt 校验,测偏了
                            .silentTimeoutMs(70_000L)
                            .requestTimeoutMs(60_000L)
                            .build());
            assertTrue(ex.getMessage().contains("silentTimeoutMs"),
                    "报错要指明是哪个字段的问题");
    }
}
