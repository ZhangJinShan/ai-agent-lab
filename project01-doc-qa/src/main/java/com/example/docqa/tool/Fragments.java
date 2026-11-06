package com.example.docqa.tool;

import com.example.docqa.retriever.RetrievedChunk;

import java.util.List;

/** 检索片段拼装:固定管线和 @Tool 共用 */
public final class Fragments {

    private Fragments(){}

    public static String format(List<RetrievedChunk> chunks){
        StringBuilder fragments = new StringBuilder();
        for (RetrievedChunk chunk : chunks) {
            fragments.append("[来源：").append(chunk.source()).append("#").append(chunk.heading()).append("]\n")
                    .append(" --- 得分是:[").append(chunk.score()).append("]\n")
                    .append(chunk.text()).append("\n\n");
        }
        return fragments.toString();
    }
}
