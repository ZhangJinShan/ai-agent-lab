package com.example.agent;

import com.example.llm.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Agent {

    private static final int MAX_ROUNDS = 8; // 放死循环的保险丝

    private final LlmClient llm;

    private final ToolRegistry registry;

    private final ObjectMapper mapper = new ObjectMapper();

    public Agent(LlmClient llm, ToolRegistry registry) {
        this.llm = llm;
        this.registry = registry;
    }

    /**
     * 一轮 Agent 交互：内部自动处理工具调用，知道模型给出最终答案
     * @param systemPrompt
     * @param userMessage
     * @return
     */
    public String run(String systemPrompt, String userMessage){
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));

        for (int round = 0; round < MAX_ROUNDS; round++) {
            // 1. 调用 llm
            String s = llm.chatCompletions(messages, registry.toToolsJson());

        }
        return "已达到最大轮次限制,任务未完成。";
    }
}
