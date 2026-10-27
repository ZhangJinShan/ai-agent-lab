package com.example.agent;

/**
 * LLM 调用失败的统一异常:状态码放在类型字段里,而不是拼进 message 字符串。
 * 这样 RetryExecutor.isRetryable() 判的是 getStatusCode(),不是 message.contains("429")。
 */
public class LlmException extends RuntimeException{

    private final int statusCode;

    private final String body;

    public LlmException(int statusCode, String body) {
        super("LLM 调用失败,HTTP " + statusCode + ": " + truncate(body, 500));
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode(){
        return statusCode;
    }

    public String getBody(){
        return body;
    }

    private static String truncate(String s, int max){
        if (null == s) {
            return "";
        }
        return s.substring(0, Math.min(s.length(), max));
    }
}
