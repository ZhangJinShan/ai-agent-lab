package com.example.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author zhangjinshan
 * @description: Agent 智能体
 * @create: 2026/10/25
 **/
public class Agent {

    Logger logger = LoggerFactory.getLogger(Agent.class);

    private ObjectMapper mapper = new ObjectMapper();

    private final LlmClient llm;

    private final ToolRegistry registry;

    private final AgentConfig config;

    private final ConversationMemory memory;

    public Agent(LlmClient llm, ToolRegistry registry, AgentConfig config, ConversationMemory memory) {
        this.llm = llm;
        this.registry = registry;
        this.config = config;
        this.memory = memory;
    }

    /**
     * 对外唯一入口:多轮对话。内部自动维护记忆 + 处理工具调用。
     * @param userInput
     * @return
     */
    public String chat(String userInput){
        boolean ownTrace = MDC.get("traceId") == null;
        if (ownTrace) {
            MDC.put("traceId", UUID.randomUUID().toString().replace("-", ""));
        }
        // system 只在记忆为空时加入一次:newConversation() 清空后,下次 chat 自动补回
        if (memory.messages().isEmpty()) {
            memory.add(Map.of("role", "system", "content", config.getSystemPrompt()));
        }
        // 添加 user message 到 memory
        memory.add(Map.of("role", "user", "content", userInput));
        int executeRounds = 0;
        int toolCallCount = 0;
        logger.info("── Agent 开始 ────────────────────────────");
        try {
            for (int round = 0; round < config.getMaxRounds(); round++) {
                executeRounds ++;

                logger.info("[轮次 {}] 当前 messages={} 条", round + 1, messageCount());
                // 1. 调用 llm
                String response = llm.chatCompletions(memory.messages(), registry.toToolsJson());
                JsonNode root = null;
                try {
                    root = mapper.readTree(response);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("LLM 响应不是合法 JSON: " + response, e);
                }
                AssistantTurn assistantTurn = parseAssistantTurn(root);
                List<ToolCall> toolCalls = assistantTurn.toolCalls();

                // 2. 将 assistant 的消息（含 tool_calls） 原样追回 messages
                memory.add(assistantTurn.toMessage());

                // 3. 没有调用工具 -> 最终答案
                if (toolCalls.isEmpty()) {
                    String content = assistantTurn.content();
                    if (null == content || content.isEmpty()) {
                        content = "无内容";
                    }
                    logger.info("[轮次 {}] 最终答案: {}", round + 1, content);
                    return content;
                }

                // 4. 有工具调用 → 逐个执行,每个结果作为 role=tool 的消息追加
                for (ToolCall toolCall : toolCalls) {
                    logger.info("[轮次 {}] 模型请求工具: {}({})", round + 1, toolCall.name(), toolCall.argsJson());
                    String result = registry.execute(toolCall.name(), toolCall.argsJson());
                    logger.info("[轮次 {}] 工具结果: {}", round + 1, result);
                    memory.add(Map.of("role", "tool", "tool_call_id", toolCall.id(), "content", result));
                    // 统计工具调用次数
                    toolCallCount++;
                }
            }
            return "已达到最大轮次限制,任务未完成。";
        } finally {
            logger.info("── Agent 结束: 共 {} 轮,调用工具 {} 次 ──────", executeRounds, toolCallCount);
            if (ownTrace) {
                MDC.remove("traceId");
            }
        }
    }



    /** 记忆可见性:CLI 观察裁剪用 */
    public int messageCount(){
        return memory.messages().size();
    }

    /**
     * 开启新对话
     */
    public void newConversation(){
        memory.clear();
    }

    private AssistantTurn parseAssistantTurn(JsonNode node){
        JsonNode messageNode = node.path("choices").path(0).path("message");
        // 1. 装配 content
        String content = null;
        JsonNode contentNode = messageNode.path("content");
        if (!contentNode.isMissingNode() && !contentNode.isNull()) {
            content = contentNode.asText();
        }

        // 2. 装配 tool_calls
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode toolCallsNode = messageNode.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode item : toolCallsNode) {
                ToolCall toolCall = new ToolCall(
                        item.path("id").asText(),
                        item.path("function").path("name").asText(),
                        item.path("function").path("arguments").asText()
                );
                toolCalls.add(toolCall);
            }
        }

        return new AssistantTurn(content, toolCalls);
    }
}
