package com.example.agent;

import java.util.List;

/** 一轮助手回复:可能有 content、可能有 toolCalls、可能两者都有 */
public record AssistantTurn(String content, List<ToolCall> toolCalls) {
}
