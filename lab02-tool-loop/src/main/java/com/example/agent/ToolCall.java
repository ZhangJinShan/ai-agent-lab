package com.example.agent;

/** 单个工具调用请求:id 是协议凭证,name 是工具名,argsJson 是参数 JSON 字符串 */
public record ToolCall(String id, String name, String argsJson) {
}
