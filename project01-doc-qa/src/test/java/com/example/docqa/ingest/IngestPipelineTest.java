package com.example.docqa.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 摄入流水线串接测试:加载 → 切割 → 转换 → 入库 → 检索,一条龙。
 * 用 FakeEmbeddingModel 固定向量 —— 测管线串接,不测向量质量(那是第 8 周评估的活)。
 * 块数全部手算,不许现算:3 篇文档 = 2 + 3 + 1 = 6 块。
 */
class IngestPipelineTest {

    @Test
    void 流水线串接_六块入库_元数据正确(@TempDir Path dir) throws IOException {
        // 造文档:每篇的切块数可手算
        Files.writeString(dir.resolve("接口文档.md"),
                "## 甲\n" + "a".repeat(100) + "\n## 乙\n" + "b".repeat(100));   // 2 小节 → 2 块
        Files.writeString(dir.resolve("故障手册.md"),
                "## 超长小节\n" + "c".repeat(2000));                            // 2000 字滑窗 → 3 块
        Files.writeString(dir.resolve("配置说明.md"),
                "## 小节\n" + "d".repeat(50));                                  // → 1 块

        // ① 加载 ② 切割(ingest.Document 与 Spring 的 Document 同名,这里用全限定)
        List<com.example.docqa.ingest.Document> docs = new DocumentLoader(dir.toString()).loadAll();
        TextSplitter splitter = new TextSplitter();
        List<Chunk> chunks = new ArrayList<>();
        for (com.example.docqa.ingest.Document doc : docs) {
            chunks.addAll(splitter.split(doc));
        }
        assertEquals(6, chunks.size(), "手算:2 + 3 + 1 = 6 块");

        // ③④ 转换 + 入库(假向量,链路完整可跑)
        //    注:生产里的转换在 IngestCommand.toSpringDoc,两边逻辑必须一致 ——
        //    测试自带一份是为了断言独立,改生产转换时同步这里(更优做法:抽公共静态方法共用)
        VectorStore store = SimpleVectorStore.builder(new FakeEmbeddingModel()).build();
        store.add(chunks.stream().map(this::toSpringDoc).toList());

        // 全部入库:topK 拉满,应查回 6 条
        List<Document> all = store.similaritySearch(
                SearchRequest.builder().query("任意查询").topK(100).build());
        assertEquals(6, all.size());

        // 元数据三件套进 metadata 了:(source, chunkIndex) 组合和手算一致
        Set<String> 实际组合 = new HashSet<>();
        for (Document d : all) {
            实际组合.add(d.getMetadata().get("source") + "#" + d.getMetadata().get("chunkIndex"));
        }
        assertEquals(Set.of(
                "接口文档.md#0", "接口文档.md#1",
                "故障手册.md#0", "故障手册.md#1", "故障手册.md#2",
                "配置说明.md#0"), 实际组合);

        // 尾块截断正确:2000 字滑窗的第 3 块(下标 2)长 600
        Document 尾块 = all.stream()
                .filter(d -> "故障手册.md".equals(d.getMetadata().get("source"))
                        && "2".equals(String.valueOf(d.getMetadata().get("chunkIndex"))))
                .findFirst().orElseThrow();
        assertEquals(600, 尾块.getText().length());

        // 检索链路:topK=3 拿到 3 条(假向量全相等,顺序不保证,只验「查得到」)
        assertEquals(3, store.similaritySearch(
                SearchRequest.builder().query("限流").topK(3).build()).size());
    }

    /** 与 IngestCommand.toSpringDoc 保持一致(见类注释) */
    private Document toSpringDoc(Chunk chunk) {
        return new Document(chunk.text(),
                Map.of("source", chunk.source(),
                        "heading", chunk.heading(),
                        "chunkIndex", String.valueOf(chunk.chunkIndex())));
    }
}
