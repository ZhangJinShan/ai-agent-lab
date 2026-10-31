package com.example.tool;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** 一轮助手回复:可能有 content、可能有 toolCalls、可能两者都有 */
public record AssistantTurn(String content, List<ToolCall> toolCalls) {

    /**
     * 协议红线:assistant 消息(含 tool_calls)必须原样追加回 messages
     * @return
     */
    public Map<String, Object> toMessage(){
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        if (null != content && !content.isEmpty()) {
            message.put("content", content);
        }
        if (!toolCalls.isEmpty()) {
            message.put("tool_calls", toolCalls);
        }
        return message;
    }
}
