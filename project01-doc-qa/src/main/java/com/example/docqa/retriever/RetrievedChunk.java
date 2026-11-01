package com.example.docqa.retriever;

/**
 * 一个检索结果：块文本 + 可引用的出处 + 相似度分数
 * 刻意不暴露 Spring AI 的 Document —— 领域模型不泄框架类型,以后换库不用改业务代码。
 */
public record RetrievedChunk(
        String text,
        String source,
        String heading,
        double score
) {
}
