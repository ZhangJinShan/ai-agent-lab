package com.example.docqa;

import com.example.docqa.ingest.Chunk;
import com.example.docqa.ingest.Document;
import com.example.docqa.ingest.DocumentLoader;
import com.example.docqa.ingest.TextSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class Project01DocQaApplication {

	private static final Logger log = LoggerFactory.getLogger(Project01DocQaApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(Project01DocQaApplication.class, args);
	}

	@Bean
	DocumentLoader documentLoader(@Value("${docqa.docs-dir}") String dir){
		return new DocumentLoader(dir);
	}

	@Bean
	TextSplitter textSplitter(){
		return new TextSplitter();
	}

	@Bean
	VectorStore vectorStore(EmbeddingModel embeddingModel) {
		return SimpleVectorStore.builder(embeddingModel).build();
	}
	
	@Bean
	CommandLineRunner verifyLoad(DocumentLoader loader, TextSplitter textSplitter, EmbeddingModel embeddingModel, VectorStore vectorStore) {
		return args -> {
			// 加载文档
			List<Document> docs = loader.loadAll();
			log.info("Loaded {} documents", docs.size());
			// 切割文档
			List<Chunk> chunks = new ArrayList<>();
			for (Document doc : docs) {
				int previewLen = Math.min(doc.content().length(), 100);
				log.info("  {} ({}字): {}", doc.source(), doc.content().length(), doc.content().substring(0, previewLen));
				List<Chunk> childChunks = textSplitter.split(doc);
                if (!childChunks.isEmpty()) {
                    chunks.addAll(childChunks);
                }
			}
			log.info("切出 {} 块", chunks.size());
			// 向量化：演示一次看维度
            if (!chunks.isEmpty()) {
				float[] embed = embeddingModel.embed(chunks.get(0).text());
				log.info("向量维度: {}", embed.length);
			}

			// 存储:Chunk 转 Spring AI Document(元数据进 metadata),批量入库
			List<org.springframework.ai.document.Document> springDocs = chunks.stream().map(this::toSpringDoc).toList();
			vectorStore.add(springDocs);

			// ⑤ 冒烟验证:查一句文档里有的问题,top-3 应命中「登录接口」那个块
			var results = vectorStore.similaritySearch(
					SearchRequest.builder().query("登录接口的限流是多少").topK(3).build());
			for (org.springframework.ai.document.Document r : results) {
				String text = r.getText();
                if (null == text || text.isBlank()) {
                    log.warn("Document.getText() is null or blank");
					continue;
                }
				log.info("[命中] {}#{}: {}", r.getMetadata().get("source"),
						r.getMetadata().get("heading"), text.substring(0, 40));
			}
		};
	}

	private org.springframework.ai.document.Document toSpringDoc(Chunk chunk) {
		// source/heading/chunkIndex 进 metadata —— 第 6 周引用标注靠它,别漏
		return new org.springframework.ai.document.Document(
				chunk.text(),
				Map.of("source", chunk.source(),
						"heading", chunk.heading(),
						"chunkIndex", String.valueOf(chunk.chunkIndex())));
	}
}
