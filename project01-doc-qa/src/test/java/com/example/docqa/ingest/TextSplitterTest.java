package com.example.docqa.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author zhangjinshan
 * @description: 文本分割器单元测试
 * @create: 2026/10/31
 **/
public class TextSplitterTest {

    private final TextSplitter splitter = new TextSplitter();

    @Test
    void 普通文档_三个小节_各切一块() {
        Document doc = new Document("接口文档.md", """
                # 用户中心接口文档

                ## 登录接口
                账号密码登录,换取访问令牌。
                ## 用户信息查询接口
                按用户 ID 查询基本信息。
                ## 修改密码接口
                修改当前登录用户的密码。
                """);
        List<Chunk> chunks = splitter.split(doc);

        // 注意:文档大标题「用户中心接口文档」下面没有正文,
        // 被 isBlank 过滤丢弃,所以是 3 块不是 4 块
        assertEquals(3, chunks.size());
        assertEquals("登录接口", chunks.get(0).heading());
        assertEquals("用户信息查询接口", chunks.get(1).heading());
        assertEquals("修改密码接口", chunks.get(2).heading());
    }

    @Test
    void 超长小节_滑窗三块_重叠恰好一百字() {
        // 一个 2000 字的超长小节
        Document doc = new Document("故障手册.md", "## 超长小节\n" + "a".repeat(2000));
        List<Chunk> chunks = splitter.split(doc);

        // 块数 = ceil((2000-100)/700) = 3;块长 800 / 800 / 600
        assertEquals(3, chunks.size());
        assertEquals(800, chunks.get(0).text().length());
        assertEquals(800, chunks.get(1).text().length());
        assertEquals(600, chunks.get(2).text().length());

        // 灵魂断言:第 2 块开头 100 字 == 第 1 块结尾 100 字
        assertEquals(chunks.get(0).text().substring(700),
                chunks.get(1).text().substring(0, 100));
        assertEquals(chunks.get(1).text().substring(700),
                chunks.get(2).text().substring(0, 100));
    }

    @Test
    void 元数据_索引连续_标题各归各位() {
        Document doc = new Document("配置说明.md", """
                前言内容

                ## 甲
                甲的内容
                ### 乙
                乙的内容
                """);
        List<Chunk> chunks = splitter.split(doc);

        assertEquals(3, chunks.size());
        assertEquals("", chunks.get(0).heading());        // 前言 heading = 空串
        assertEquals("甲", chunks.get(1).heading());
        assertEquals("乙", chunks.get(2).heading());       // heading 取最近的标题(含 ###)
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).chunkIndex());  // 0,1,2 连续
            assertEquals("配置说明.md", chunks.get(i).source());
        }
    }

    @Test
    void 边界_空文本零块_恰好八百一块_八百零一两块() {
        assertEquals(0, splitter.split(new Document("a.md", "")).size());
        assertEquals(1, splitter.split(new Document("a.md", "b".repeat(800))).size());
        assertEquals(2, splitter.split(new Document("a.md", "b".repeat(801))).size());
    }
}
