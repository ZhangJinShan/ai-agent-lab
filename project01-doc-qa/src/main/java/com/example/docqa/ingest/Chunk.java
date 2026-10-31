package com.example.docqa.ingest;

/**
 * 一个检索单元:原文切片 + 可追溯的元数据。
 */
public record Chunk(
        String source,      // 来自哪个文件,如 "接口文档.md"
        String heading,     // 所在小节标题,如 "登录接口"
        int chunkIndex,     // 全文档内第几块(0 起),供追溯
        String text         // 块正文(≤800 字,相邻块可重叠)
) {
}
