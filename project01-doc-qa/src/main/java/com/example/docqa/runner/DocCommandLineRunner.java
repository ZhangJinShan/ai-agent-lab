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
@ConditionalOnProperty(name = "doc-qa.runner.doc", havingValue = "true")
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
                                @Value("${doc-qa.retrieval.top-k:5}") int topK,
                                @Value("${doc-qa.retrieval.min-score:0.37}") double minScore,
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

        Retriever retriever = initRetriever();

        // 路径 A
        AnswerService answerService = new AnswerService(retriever, new ChatClientAnswerGenerator(chatClient), minScore, "w7d4-A", queryRewriter, chatMemory);

        // 路径 B
        DocQaTools docQaTools = new DocQaTools(retriever, minScore);

        List<String> questions = List.of(
                "你好,你是谁?",
                "登录接口的限流是多少?",
                "登录限流和密码锁定策略,哪个更严格?",
                "明天的天气怎么样?"
        );

        for (String q : questions) {
            String a = answerService.answer(q);
            log.info("[A] {} → {}", q, a);

            String b = agenticClient.prompt()
                    .system(AnswerService.SYSTEM_TEMPLATE)   // 同样的 system 规则,控制变量
                    .user(q)
                    .tools(docQaTools)
                    .call().content();
            log.info("[B] {} → {}", q, b);
        }
    }


    private Retriever initRetriever() throws Exception {
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
        return new Retriever(vectorStore, topK);
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
