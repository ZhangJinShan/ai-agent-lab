package com.example.docqa.ingest;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake EmbeddingModel —— FakeLlmServer 思想的 Spring 版:
 * 把不确定的外部依赖(真 API)换成确定的桩,测「自己的管线写对没」,不测向量质量。
 * 所有文本都返回同一个固定 3 维向量:相似度全相等,但 add/search 链路完整可跑。
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    @Override
    public float[] embed(Document document) {
        return fixedVector();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(fixedVector(), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    private float[] fixedVector() {
        return new float[]{1f, 0f, 0f};
    }
}
