package com.example.docqa.runner;

import com.example.docqa.ingest.Chunk;
import com.example.docqa.ingest.Document;
import com.example.docqa.ingest.DocumentLoader;
import com.example.docqa.ingest.TextSplitter;
import com.example.docqa.qa.AnswerService;
import com.example.docqa.qa.ChatClientAnswerGenerator;
import com.example.docqa.qa.QueryRewriter;
import com.example.docqa.retriever.Retriever;
import com.example.docqa.tool.DocQaTools;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
@ConditionalOnProperty(name = "docqa.runner.doc", havingValue = "true", matchIfMissing = true)
@Component
public class DocCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DocCommandLineRunner.class);

    private final DocumentLoader loader;
    private final TextSplitter textSplitter;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final int topK;
    private final double minScore;
    private final QueryRewriter queryRewriter;
    private final ChatMemory chatMemory;

    private final ChatClient agenticClient;

    public DocCommandLineRunner(DocumentLoader loader,
                                TextSplitter textSplitter,
                                EmbeddingModel embeddingModel,
                                VectorStore vectorStore,
                                @Qualifier(value = "chatClient") ChatClient chatClient,
                                @Value("${docqa.retrieval.top-k:5}") int topK,
                                @Value("${docqa.retrieval.min-score:0.3}") double minScore,
                                QueryRewriter queryRewriter,
                                ChatMemory chatMemory,
                                @Qualifier(value = "agenticClient") ChatClient agenticClient) {
        this.loader = loader;
        this.textSplitter = textSplitter;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.topK = topK;
        this.minScore = minScore;
        this.queryRewriter = queryRewriter;
        this.chatMemory = chatMemory;
        this.agenticClient = agenticClient;
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

        // 初始化检索器
        Retriever retriever = new Retriever(vectorStore, topK);

//        // ⑥ 问答演示(Day 2):检索 → 拼引用片段 → 调 chat → 带引用回答
//        AnswerService answerService = new AnswerService(retriever, new ChatClientAnswerGenerator(chatClient), minScore, "repl", queryRewriter, chatMemory);
//        List<String> questions = List.of(
//                "我叫老张,记住这个名字",
//                "我叫什么?",
//                "登录接口的限流是多少?",
//                "那密码错误呢?"
//        );
//
//        for (String question : questions) {
//            String answer = answerService.answer(question);
//            log.info("{} → 回答是: {}", question, answer);
//        }

        DocQaTools docQaTools = new DocQaTools(retriever, minScore);

        for (String q : List.of(
                "登录接口的限流是多少?",                    // 该调工具
                "你好,你是谁?",                            // 不该调
                "登录限流和密码锁定策略,哪个更严格?")) {     // 多跳,可能连查两次
            String a = agenticClient.prompt()
                    .system(AnswerService.SYSTEM_TEMPLATE)
                    .user(q)
                    .tools(docQaTools)
                    .call()
                    .content();
            log.info("[agentic] {} → {}", q, a);
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
