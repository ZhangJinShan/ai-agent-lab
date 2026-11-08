package com.example.docqa.config;

import com.example.docqa.ingest.DocumentLoader;
import com.example.docqa.ingest.TextSplitter;
import com.example.docqa.qa.QueryRewriter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhangjinshan
 * @description: 文档 QA 相关 Bean 的集中配置
 * @create: 2026/11/1
 **/
@Configuration
public class DocQaConfig {

	@Bean
	DocumentLoader documentLoader(@Value("${doc-qa.docs-dir}") String dir) {
		return new DocumentLoader(dir);
	}

	@Bean
	TextSplitter textSplitter() {
		return new TextSplitter();
	}

	@Bean
	VectorStore vectorStore(EmbeddingModel embeddingModel) {
		return SimpleVectorStore.builder(embeddingModel).build();
	}

	@Bean
	ChatMemory chatMemory(@Value("${doc-qa.max-message:20}") int maxMessages){
		return MessageWindowChatMemory.builder().maxMessages(maxMessages).build();
	}

	@Bean
	QueryRewriter queryRewriter(ChatModel chatModel, ChatMemory chatMemory){
		return new QueryRewriter(chatModel, chatMemory);
	}

	@Bean(name = "chatClient")
	ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
		return ChatClient.builder(chatModel)
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
				.build();
	}

	@Bean(name = "agenticClient")
	ChatClient agenticClient(ChatModel chatModel){
		return ChatClient.builder(chatModel).build();
	}
}
