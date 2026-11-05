package com.example.docqa.runner;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@ConditionalOnProperty(name = "docqa.runner.chat", havingValue = "true", matchIfMissing = true)
@Component
public class ChatCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatCommandLineRunner.class);

    private final ChatClient chatClient;

    private final ChatMemory chatMemory;

    private static final String SYSTEM_TEMPLATE = """
            你是一个智能助手，可以帮我解答一些问题。
            """;

    public ChatCommandLineRunner(ChatClient chatClient, ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }

    private void runChat(String systemPrompt, String userPrompt, Runnable run){

    }

    @Override
    public void run(String @NonNull ... args){

        String sessionId = UUID.randomUUID().toString().replace("-", "");



        // 第一轮
        chatClient.prompt()
                .system(SYSTEM_TEMPLATE)
                .user("")
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call().content();
    }


}
