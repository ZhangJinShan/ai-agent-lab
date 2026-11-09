package com.example.docqa.runner;

import com.example.docqa.eval.AnswerJudge;
import com.example.docqa.eval.DatasetLoader;
import com.example.docqa.eval.EvalQuestion;
import com.example.docqa.eval.EvalRunner;
import com.example.docqa.eval.JudgeVerdict;
import com.example.docqa.eval.RunSummary;
import com.example.docqa.ingest.Chunk;
import com.example.docqa.ingest.Document;
import com.example.docqa.ingest.DocumentLoader;
import com.example.docqa.ingest.TextSplitter;
import com.example.docqa.qa.AnswerService;
import com.example.docqa.qa.ChatClientAnswerGenerator;
import com.example.docqa.retriever.Retriever;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author zhangjinshan
 * @description: Eval Command Line Runner
 * @create: 2026/11/8
 **/
@ConditionalOnProperty(name = "doc-qa.runner.eval", havingValue = "true")
@Component
public class EvalCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalCommandLineRunner.class);

    private final DocumentLoader documentLoader;

    private final TextSplitter textSplitter;

    private final VectorStore vectorStore;

    private final ChatClient agenticClient;

    private final int topK;

    private final double minScore;

    public EvalCommandLineRunner(DocumentLoader documentLoader,
                                 TextSplitter textSplitter,
                                 VectorStore vectorStore,
                                 @Qualifier("agenticClient") ChatClient agenticClient,
                                 @Value("${doc-qa.retrieval.top-k:5}") int topK,
                                 @Value("${doc-qa.retrieval.min-score:0.37}") double minScore) {
        this.documentLoader = documentLoader;
        this.textSplitter = textSplitter;
        this.vectorStore = vectorStore;
        this.agenticClient = agenticClient;
        this.topK = topK;
        this.minScore = minScore;
    }

    @Override
    public void run(@NotNull String... args) throws Exception {
        Retriever retriever = initRetriever();
        AnswerService answerService = new AnswerService(retriever, new ChatClientAnswerGenerator(agenticClient), minScore);
        EvalRunner runner = new EvalRunner(answerService, new AnswerJudge(agenticClient));

        RunSummary summary = runner.run(Path.of("eval/dataset-50.jsonl"), Path.of("eval/results-run1.md"));
        log.info("评估完成: {}", summary);
    }

    private Retriever initRetriever() throws Exception {
        // 加载文档
        List<Document> docs = documentLoader.loadAll();

        // 切割文档
        List<Chunk> chunks = new ArrayList<>();
        for (Document doc : docs) {
            List<Chunk> childChunks = textSplitter.split(doc);
            if (!childChunks.isEmpty()) {
                chunks.addAll(childChunks);
            }
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
