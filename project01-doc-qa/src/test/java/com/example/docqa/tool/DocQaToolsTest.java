package com.example.docqa.tool;

import com.example.docqa.retriever.FakeRetriever;
import com.example.docqa.retriever.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DocQaToolsTest {
    private static final double MIN_SCORE = 0.37;

    private DocQaTools toolsWith(Map<String, List<RetrievedChunk>> data) {
        return new DocQaTools(new FakeRetriever(data), MIN_SCORE);
    }

    @Test
    void 命中_返回带来源的片段() {
        var tools = toolsWith(Map.of("限流", List.of(
                new RetrievedChunk("登录限流 100 次/分钟", "接口文档.md", "登录接口", 0.82))));
        String result = tools.searchDocs("限流");
        assertTrue(result.contains("接口文档.md#登录接口"));
        assertTrue(result.contains("登录限流 100 次/分钟"));
    }

    @Test
    void 低分_返回错误文本() {
        var tools = toolsWith(Map.of("灰度", List.of(
                new RetrievedChunk("灰度相关描述", "配置说明.md", "开关配置", 0.18))));
        assertEquals("知识库中没有相关内容", tools.searchDocs("灰度"));
    }

    @Test
    void 空结果_返回错误文本() {
        var tools = toolsWith(Map.of());
        assertEquals("知识库中没有相关内容", tools.searchDocs("某个不存在的配置项"));
    }
}