package com.example.docqa.retriever;

import java.util.List;
import java.util.Map;

/**
 * @author zhangjinshan
 * @description: 测试桩：不碰 VectorStore， 按 [问题的关键词 → 预定的 RetrievedChunk 列表] 返回
 * @create: 2026/11/1
 **/
public class FakeRetriever extends Retriever {

    private final Map<String, List<RetrievedChunk>> answers;

    public FakeRetriever(Map<String, List<RetrievedChunk>> answers) {
        super(null); // retrieve被重写， 不会通道 vectorStore
        this.answers = answers;
    }

    @Override
    public List<RetrievedChunk> retrieve(String question) {
        for (Map.Entry<String, List<RetrievedChunk>> e : answers.entrySet()) {
            if (question.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return List.of();
    }
}
