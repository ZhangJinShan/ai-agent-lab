package com.example.docqa.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentLoader 测试:@TempDir 临时目录造文件,不碰 docs/,离线可跑。
 * 断言目标是「加载逻辑」:递归、过滤、编码、防御 —— 不测 PDF 解析质量。
 */
class DocumentLoaderTest {

    @Test
    void 递归_子目录里的md也被读到(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("根文档.md"), "根内容");
        Files.createDirectories(dir.resolve("子目录"));
        Files.writeString(dir.resolve("子目录/嵌套文档.md"), "嵌套内容");

        List<Document> docs = new DocumentLoader(dir.toString()).loadAll();

        assertEquals(2, docs.size());
        List<String> sources = docs.stream().map(Document::source).toList();
        assertTrue(sources.contains("根文档.md"));
        assertTrue(sources.contains("子目录/嵌套文档.md"));  // source 是相对路径,分隔符已统一成 /
    }

    @Test
    void 过滤_只收md_跳过txt和隐藏文件(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("文档.md"), "内容");
        Files.writeString(dir.resolve("笔记.txt"), "不收");
        Files.writeString(dir.resolve(".DS_Store"), "垃圾");
        Files.writeString(dir.resolve(".隐藏.md"), "也不收");

        List<Document> docs = new DocumentLoader(dir.toString()).loadAll();

        assertEquals(1, docs.size());
        assertEquals("文档.md", docs.get(0).source());
    }

    @Test
    void 编码_UTF8中文读回一致(@TempDir Path dir) throws IOException {
        String 中文 = "登录接口限流 100 次/分钟,超过返回 429。";
        Files.writeString(dir.resolve("中文.md"), 中文, StandardCharsets.UTF_8);

        List<Document> docs = new DocumentLoader(dir.toString()).loadAll();

        assertEquals(中文, docs.get(0).content());
    }

    @Test
    void 空目录返回空列表_目录不存在构造即抛(@TempDir Path dir) {
        // 空目录 → 0 篇,不报错
        assertDoesNotThrow(() -> {
            assertTrue(new DocumentLoader(dir.toString()).loadAll().isEmpty());
        });

        // 不存在的目录 → 构造函数就抛 IllegalArgumentException(Day 1 防御点 1)
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentLoader(dir.resolve("不存在").toString()));
    }

    @Test
    void 坏pdf跳过不阻塞整批(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("好文档.md"), "正常内容");
        Files.writeString(dir.resolve("空文档.pdf"), "");   // 抽不出文字 → 警告跳过
        // 别用「纯文本改名 .pdf」造坏文件:Tika 按内容嗅探格式,
        // 纯文本会被当 text/plain 成功解析 —— 空文件才是稳定的「抽不出文字」场景

        List<Document> docs = new DocumentLoader(dir.toString()).loadAll();

        assertEquals(1, docs.size(), "坏 PDF 被跳过,好文档照常加载 —— 单篇失败不阻塞整批");
        assertEquals("好文档.md", docs.get(0).source());
    }
}
