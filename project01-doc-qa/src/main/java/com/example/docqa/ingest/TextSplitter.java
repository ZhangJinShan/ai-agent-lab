package com.example.docqa.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author zhangjinshan
 * @description: 文本分割器
 * @create: 2026/10/31
 **/
public class TextSplitter {

    private static final int MAX_CHUNK_SIZE = 800;   // 块上限(字)

    private static final int OVERLAP = 100;          // 相邻块重叠(字)

    /**
     * 标题行判定:#~###### 后跟空白才算(markdown 规范)。
     * 别用 startsWith("#") —— 代码里的 #include、shell 的 # 注释会被误判成标题。
     */
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+.*");

    /** 一篇 Document → 若干 Chunk */
    public List<Chunk> split(Document doc) {
        List<Chunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (Section section : splitSections(doc.content())) {
            for (String block : slideWindow(section.text)) {
                chunks.add(new Chunk(doc.source(), section.heading(), chunkIndex ++, block));
            }
        }
        return  chunks;
    }

    /** 一个小节 = 一个标题 + 它下面的正文（到下一个标题为止） */
    private record Section(String heading, String text){}

    /**
     * 按照标题行分节
     * @param content
     * @return
     */
    private List<Section> splitSections(String content){
        List<Section> sections = new ArrayList<>();
        String heading = ""; // 前言（第一个标题之前）的 heading 约定为空字符串
        StringBuilder buf = new StringBuilder();
        // -1 保留结尾空行，换行信息不丢
        for (String line : content.split("\n", -1)) {
            if (HEADING.matcher(line).matches()) {
                // 遇到新标题 → 先收割当前节
                // trim 后为空的节（如文档大标题和第一个小标题之间只有空行）直接丢弃
                // 它没有检索价值，留着会切出一块纯空白
                if (!buf.toString().isBlank()) { // 这个是上一个 section
                    sections.add(new Section(heading, buf.toString()));
                }
                buf.setLength(0);
                // heading 去 [最近的标题行]，去掉 # 前缀和收尾空格
                heading = line.replaceFirst("^#{1,6}\\s+", "").trim();
            } else {
                if (!buf.isEmpty()) {
                    buf.append("\n"); // 用换行将行重新拼回原文
                }
                buf.append(line);
            }
        }
        // 别漏最后一节:它后面没有标题行来触发收割
        if (!buf.toString().isBlank()) {
            sections.add(new Section(heading, buf.toString()));
        }
        return sections;
    }

    /** 滑动窗口切一段长文本,返回文本块列表(元数据在 split 里统一打) */
    private List<String> slideWindow(String text) {
        List<String> blocks = new ArrayList<>();
        if (text.isBlank()) {
            return blocks; // 空文本 → 0 块
        }
        int start = 0;
        while (true) {
            // 尾块必须截断到实际长度，否则 substring 越界
            int end = Math.min(start + MAX_CHUNK_SIZE, text.length());
            blocks.add(text.substring(start, end));
            if (end >= text.length()) {
                // 到末尾就停。注意「恰好 800 字」也从这里退出 → 只有 1 块,
                // 不会再多切一刀
                break;
            }
            // 步进 = 上限 - 重叠 = 700。下一块从 start+700 开始,
            // 它的开头 100 字 == 上一块的结尾 100 字(左闭右开,注意别算错 1)
            start += MAX_CHUNK_SIZE - OVERLAP;
        }
        return blocks;
    }
}
