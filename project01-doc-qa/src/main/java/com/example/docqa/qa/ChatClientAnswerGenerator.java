package com.example.docqa.qa;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * @author zhangjinshan
 * @description:
 * @create: 2026/11/1
 **/
public class ChatClientAnswerGenerator implements AnswerGenerator {

    private final ChatClient chatClient;

    public ChatClientAnswerGenerator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .options(ChatOptions.builder().temperature(0.3).maxTokens(1024))
                .call()
                .content();
    }
}
