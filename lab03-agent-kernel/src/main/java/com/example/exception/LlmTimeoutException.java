package com.example.exception;

/**
 * @author zhangjinshan
 * @description: 流式静默超时:一段时间没收到任何 chunk。属于「网络类」错误,可重试
 * @create: 2026/10/27
 **/
public class LlmTimeoutException extends RuntimeException{

    public LlmTimeoutException(String message) { super(message); }
}
