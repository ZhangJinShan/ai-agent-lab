package com.example.agent;

import com.example.exception.LlmException;
import org.junit.jupiter.api.Test;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一手法:计数器数 action 执行了几次 —— 1 = 没重试,3 = 重试到次数耗尽。
 */
public class RetryExecutorTest {

    @Test
    void 测试1_5xx服务端抖动_可重试() {
        RetryExecutor retry = new RetryExecutor(3, 5);
        AtomicInteger calls = new AtomicInteger();
        String result = retry.execute(() -> {
            if (calls.incrementAndGet() <= 2) {
                throw new LlmException(500, "{\"error\":\"internal error\"}");
            }
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(3, calls.get(), "前两次 500,第 3 次成功");
    }

    @Test
    void 测试2_429限流_可重试() {
        RetryExecutor retry = new RetryExecutor(3, 5);
        AtomicInteger calls = new AtomicInteger();
        String result = retry.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new LlmException(429, "rate limited");
            }
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(2, calls.get(), "限流等一会就好,退避正好让出配额");
    }

    @Test
    void 测试3_400请求不合法_不重试() {
        RetryExecutor retry = new RetryExecutor(3, 5);
        AtomicInteger calls = new AtomicInteger();
        LlmException ex = assertThrows(LlmException.class, () -> retry.execute(() -> {
            calls.incrementAndGet();
            throw new LlmException(400, "bad request");
        }));
        assertEquals(400, ex.getStatusCode());
        assertEquals(1, calls.get(), "请求本身有 bug,重试一万次还是 400");
    }

    @Test
    void 测试4_401鉴权失败_不重试() {
        RetryExecutor retry = new RetryExecutor(3, 5);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(LlmException.class, () -> retry.execute(() -> {
            calls.incrementAndGet();
            throw new LlmException(401, "invalid api key");
        }));
        assertEquals(1, calls.get(), "key 错了,重试不会变对");
    }

    @Test
    void 测试5_截断_不重试() {
        // finish_reason=length:同样输入 + 同样 max_tokens,还是截断 —— 第 1 周 Day 4 埋的伏笔,今天收回!
        RetryExecutor retry = new RetryExecutor(3, 5);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(RuntimeException.class, () -> retry.execute(() -> {
            calls.incrementAndGet();
            throw new RuntimeException("输出被 max_tokens 截断,JSON 不完整,请调大 max_tokens 或缩短输入");
        }));
        assertEquals(1, calls.get(), "截断重试也没用,别浪费次数");
    }

    @Test
    void 测试6_超时_可重试() {
        RetryExecutor retry = new RetryExecutor(3, 5);
        AtomicInteger calls = new AtomicInteger();
        String result = retry.execute(() -> {
            if (calls.incrementAndGet() <= 2) {
                throw new HttpTimeoutException("connect timed out");
            }
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(3, calls.get(), "网络抖动,重试能改变结果");
    }

    @Test
    void 测试7_被包了一层的网络错误_沿cause链也能认出来() {
        // LlmClient 历史上爱 new RuntimeException(e) 一层层包,
        // 所以 isRetryable 要沿 getCause() 走,不然真正的错误类型被埋了
        RetryExecutor retry = new RetryExecutor(3, 5);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(RuntimeException.class, () -> retry.execute(() -> {
            calls.incrementAndGet();
            throw new RuntimeException(new HttpTimeoutException("read timed out"));
        }));
        assertEquals(3, calls.get(), "cause 链里的超时也该被认出来");
    }
}
