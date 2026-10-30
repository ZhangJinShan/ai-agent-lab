package com.example.agent;

import com.example.llm.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Agent {

    Logger logger = LoggerFactory.getLogger(getClass());

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

        int executeRounds = 0;
        int toolCallCount = 0;
        logger.info("── Agent 开始 ────────────────────────────");
        try {
            for (int round = 0; round < MAX_ROUNDS; round++) {
                executeRounds ++;
                logger.info("[轮次 {}] 请求 messages = {} 条", round + 1, messages.size());
                // 1. 调用 llm
                String response = llm.chatCompletions(messages, registry.toToolsJson());
                JsonNode root = mapper.readTree(response);
                AssistantTurn assistantTurn = parseAssistantTurn(root);
                List<ToolCall> toolCalls = assistantTurn.toolCalls();

                // 2. 把 assistant 的消息(含 tool_calls)原样追加回 messages —— 协议红线!
                messages.add(assistantTurn.toMessage());

                // 3. 没有工具调用 → 最终答案
                if (toolCalls.isEmpty()) {
                    String content = assistantTurn.content();
                    logger.info("[轮次 {}] 最终答案: {}", round + 1, content);
                    return content;
                }

                // 4. 有工具调用 → 逐个执行,每个结果作为 role=tool 的消息追加
                for (ToolCall toolCall : toolCalls) {
                    logger.info("[轮次 {}] 模型请求工具: {}({})", round + 1, toolCall.function().name(), toolCall.function().arguments());
                    String result = registry.execute(toolCall.function().name(), toolCall.function().arguments());
                    logger.info("[轮次 {}] 工具结果: {}", round + 1, result);
                    messages.add(Map.of("role", "tool", "tool_id", toolCall.id(), "content", result));
                    // 统计工具调用次数
                    toolCallCount++;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            logger.info("── Agent 结束: 共 {} 轮,调用工具 {} 次 ──────", executeRounds, toolCallCount);
        }
        return "已达到最大轮次限制,任务未完成。";
    }

    public AssistantTurn parseAssistantTurn(JsonNode root){
        JsonNode messageNode = root.path("choices").path(0).path("message");
        // 1. 装配 content
        String content = null;
        JsonNode contentNode = messageNode.path("content");
        if (!contentNode.isMissingNode() && !contentNode.isNull()) {
            content = contentNode.asText();
        }

        // 2. 装配 tool_calls
        List<ToolCall> toolCalls = new  ArrayList<>();
        JsonNode toolCallsNode = messageNode.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode item : toolCallsNode) {
                ToolCall toolCall = new ToolCall(
                        item.path("id").asText(),
                        item.path("type").asText(),
                        new ToolCallFunction(
                                item.path("function").path("name").asText(),
                                item.path("function").path("arguments").asText()
                        )
                );
                toolCalls.add(toolCall);
            }
        }
        
        return new AssistantTurn(content, toolCalls);
    }
}
