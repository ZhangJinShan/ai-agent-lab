package com.example.docqa.qa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author zhangjinshan
 * @description: 追问改写:带指代的问题 → 独立完整的问题,再走检索
 * @create: 2026/11/5
 **/
public class QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);

    private static final String REWRITE_PROMPT = """
            把带指代的追问改写成不依赖对话历史、独立完整的问题,只输出改写后的问题。
            要求:
            1. 保留原问题中的专有名词、配置项名、错误码等术语,不要同义替换
            2. 原问题本身已独立完整时,原样输出
            3. 不要解释、不要加引号、不要输出多余内容
            """;

    private final ChatMemory chatMemory;

    private final ChatCaller chatCaller;

    public QueryRewriter(ChatModel chatModel, ChatMemory chatMemory){
        this.chatMemory = chatMemory;
        // 改写不挂记忆 advisor: 否则会自动被写入到记忆中去，污染历史
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        this.chatCaller = ((systemPrompt, userPrompt) -> chatClient.prompt().system(systemPrompt).user(userPrompt).call().content());
    }

    QueryRewriter(ChatMemory chatMemory, ChatCaller chatCaller) {
        this.chatMemory = chatMemory;
        this.chatCaller = chatCaller;
    }

    public String rewrite(String conversationId, String currentQuestion){
        // 1. 取历史（框架版记忆）
        List<Message> history = chatMemory.get(conversationId);
        if (history.isEmpty()) {
            return currentQuestion; // 首轮无记忆，省一次 chat 调用（节约成本）
        }
        String context = history.stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .map(Message::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));

        // 2. 一次 chat 调用；空/异常/超时一律兜底回原问题(改写不该是单点)
        String rewritten;

        try {
            rewritten = chatCaller.call(REWRITE_PROMPT,  "对话历史:\n" + context + "\n\n当前问题:" + currentQuestion);
        } catch (Exception e) {
            log.warn("改写失败,兜底回原问题: {}", currentQuestion, e);
            return currentQuestion;
        }
        rewritten = rewritten.trim();

        // 3. 原问题 → 改写后问题(W4D1 可观测肌肉)
        log.info("QueryRewriter: [{}] -> [{}]", currentQuestion, rewritten);
        return rewritten;
    }

    /** 一次 chat 调用抽成接口,测试时换 fake(同 AnswerGenerator 的套路) */
    interface ChatCaller{
        String call(String systemPrompt, String userPrompt);
    }
}
