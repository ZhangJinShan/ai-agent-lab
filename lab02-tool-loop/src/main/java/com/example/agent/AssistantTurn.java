package com.example.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一轮助手回复:可能有 content、可能有 toolCalls、可能两者都有 */
public record AssistantTurn(String content, List<ToolCall> toolCalls) {

    public Map<String, Object> toMessage(){
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);



        return message;
    }
}
