package com.example.docqa.qa;

import org.springframework.ai.chat.client.ChatClient;

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
    public String generate(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
