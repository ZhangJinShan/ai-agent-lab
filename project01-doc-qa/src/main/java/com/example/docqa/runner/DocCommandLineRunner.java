package com.example.docqa.runner;

import com.example.docqa.ingest.Chunk;
import com.example.docqa.ingest.Document;
import com.example.docqa.ingest.DocumentLoader;
import com.example.docqa.ingest.TextSplitter;
import com.example.docqa.qa.AnswerService;
import com.example.docqa.retriever.Retriever;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author zhangjinshan
 * @description: 文档加载、切割、向量化、存储 CommanLineRunner
 * @create: 2026/11/1
 **/
@ConditionalOnProperty(name = "docqa.runner.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class DocCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DocCommandLineRunner.class);

    private final DocumentLoader loader;
    private final TextSplitter textSplitter;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public DocCommandLineRunner(DocumentLoader loader,
                                TextSplitter textSplitter,
                                EmbeddingModel embeddingModel,
                                VectorStore vectorStore,
                                ChatClient chatClient) {
        this.loader = loader;
        this.textSplitter = textSplitter;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    @Override
    public void run(@NotNull String... args) throws Exception {
        // 加载文档
        List<Document> docs = loader.loadAll();
        log.info("Loaded {} documents", docs.size());
        // 切割文档
        List<Chunk> chunks = new ArrayList<>();
        for (Document doc : docs) {
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

        // ⑥ 问答演示(Day 2):检索 → 拼引用片段 → 调 chat → 带引用回答
        Retriever retriever = new Retriever(vectorStore);
        AnswerService answerService = new AnswerService(retriever, chatClient);
        List<String> questions = List.of("登录接口的限流是多少？", "怎么处理磁盘水位告警?", "服务支持灰度发布吗?", "明天天气怎么样?");
        for (String question : questions) {
            String answer = answerService.answer(question);
            log.info("{} → 回答是: {}", question, answer);
        }
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
