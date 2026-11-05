package com.example.docqa.retriever;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

/**
 * @author zhangjinshan
 * @description: 检索器：问题 → top-k → 带分数的块
 * @create: 2026/11/1
 **/
public class Retriever {

    private static final Logger log = LoggerFactory.getLogger(Retriever.class);

    private final VectorStore vectorStore;

    private final int TOP_K;

    public Retriever(VectorStore vectorStore) {
        this(vectorStore, 5);

    }

    public Retriever(VectorStore vectorStore, int topK){
        this.vectorStore = vectorStore;
        this.TOP_K = topK;
    }

    public List<RetrievedChunk> retrieve(String question){
        // 1. 判空 → 输入不可信
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        // 2. 检索看原始分数
        SearchRequest request = SearchRequest.builder().query(question).topK(TOP_K).build();
        // 3. 相似度检索
        List<Document> docs = vectorStore.similaritySearch(request);
        // 4. 每个 org.springframework.ai.document.Document 转换为 RetrievedChunk
        return docs.stream().map(this :: toRetrievedChunk).toList();
    }

    private RetrievedChunk toRetrievedChunk(Document doc) {
        // ① getMetadata().get(...) 取出来是 Object,统一 String.valueOf
        // ② getScore() 返回 Double 可能为 null(不同 VectorStore 实现行为不一)—— 判空兜底 0.0 + warn
        Object sourceObj = doc.getMetadata().get("source");
        Object headingObj = doc.getMetadata().get("heading");
        Double scoreObj = doc.getScore();
        String source = sourceObj == null ? "" : String.valueOf(sourceObj);
        String heading = headingObj == null ? "" : String.valueOf(headingObj);
        if (scoreObj == null) {
            log.warn("Document.getScore() is null for source={}, heading={} —— 该 VectorStore 实现不吐分数,已兜底 0.0", source, heading);
        }
        return new RetrievedChunk(doc.getText(), source, heading, scoreObj == null ? 0.0 : scoreObj);
    }
}
