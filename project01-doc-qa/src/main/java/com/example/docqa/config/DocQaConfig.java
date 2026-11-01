package com.example.docqa.config;

import com.example.docqa.ingest.DocumentLoader;
import com.example.docqa.ingest.TextSplitter;
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
	DocumentLoader documentLoader(@Value("${docqa.docs-dir}") String dir) {
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
}
