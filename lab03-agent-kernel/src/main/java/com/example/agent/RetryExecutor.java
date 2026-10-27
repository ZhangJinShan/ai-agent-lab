package com.example.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 手写重试:指数退避 + 抖动。
 */
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    private final int maxAttempts;

    private final long baseDelayMs;

    /** 最近一次 execute 实际执行了几次（1=一次成功没重试），方便断言测试 */
    private volatile int lastAttemptCount;

    public RetryExecutor(int maxAttempts, long baseDelayMs) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts 至少为 1");
        }
        if (baseDelayMs < 0) {
            throw new IllegalArgumentException("baseDelayMs 不能为负");
        }

        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
    }

    /** 允许受检异常的函数式接口:httpClient.send 会抛 IOException/InterruptedException,Scheduler 塞不下 */
    @FunctionalInterface
    public interface Action<T>{
        T call() throws Exception;
    }

    /**
     * 执行 action,失败时按「可重试错误」判断 + 指数退避。
     * @param action
     * @return
     * @param <T>
     */
    public <T> T execute(Action<T> action){
        for (int attempt = 1; ; attempt ++){
            try {
                T result = action.call();
                lastAttemptCount = attempt;
                return result;
            } catch (Exception e) {
                lastAttemptCount = attempt;
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt(); // 恢复终端标志
                }
                if (attempt >= maxAttempts || !isRetryable(e)) {
                    throw wrapIfChecked(e);
                }
                // 指数退避：1s, 2s, 4s ... + 随机抖动（防惊群）
                long delay = baseDelayMs * (1L << (attempt - 1));
                delay += ThreadLocalRandom.current().nextLong(delay / 2 + 1);
                log.warn("第 {} 次失败: {} ,{} ms 后重试", attempt, describe(e), delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); // 恢复中断标识
                    throw new RuntimeException("重试等待被中断", ex);
                }

            }
        }
    }

    /**
     * RuntimeException 原样抛(类型信息不丢),受检异常包一层
     * @param e
     * @return
     */
    private RuntimeException wrapIfChecked(Exception e){
        if (e instanceof RuntimeException re) {
            return re;
        }
        return new RuntimeException(e);
    }


    /**
     * 错误分类:重试能改变结果的才重试。
     * LlmException → 看状态码(429/5xx 重试,400/401/403 不重试);
     * 网络层超时/连接失败 → 重试;JSON 坏了、截断等其他 → 不重试。
     */
    private boolean isRetryable(Throwable e){
        for (Throwable t = e; t != null; t = t.getCause()){
            if(t instanceof LlmException le){
                int statusCode = le.getStatusCode();
                return statusCode == 429 || statusCode >= 500;
            }
            if (t instanceof InterruptedException) {
                return false; // 被中断 = 要取消，不是网络抖动
            }
            if (t instanceof JsonProcessingException) {
                return false; // 响应数据本身坏了，重试还是坏
            }
            if (t instanceof LlmTimeoutException) {
                return true; // 静默超时 = 网络/服务端抖动,重试能改变结果
            }
            if (t instanceof HttpTimeoutException || t instanceof ConnectException || t instanceof IOException) {
                return true; // 超时 / 连接被拒 / 连接重置 -> 网络抖动
            }
        }
        return false; // 默认不重试: 截断、业务异常、解析失败……
    }

    public int getLastAttemptCount(){
        return lastAttemptCount;
    }


    /** 拼出整条 cause 链:ConnectException: null <- ... */
    private static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
        }
        return sb.toString();
    }
}
