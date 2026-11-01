package com.example.docqa.qa;

import com.example.docqa.retriever.RetrievedChunk;
import com.example.docqa.retriever.Retriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

/**
 * @author zhangjinshan
 * @description: 问答主链路：检索 → 拼引用片段 → 调用 chat → 带引用回答
 * @create: 2026/11/1
 **/
public class AnswerService {

    private final static Logger logger = LoggerFactory.getLogger(AnswerService.class);

    private final Retriever retriever;

    private final ChatClient chatClient;

    public AnswerService(Retriever retriever, ChatClient chatClient) {
        this.retriever = retriever;
        this.chatClient = chatClient;
    }

    public String answer(String question) {
        // 1. 检索 top-5
        List<RetrievedChunk> chunks = retriever.retrieve(question);
        if (chunks.isEmpty()) {
            return "文档中没有相关内容";
        }
        // 2. 拼 fragments → 每块格式[来源: {source}#{heading}]\n{text}\n\n
        String fragments = buildFragments(chunks);
        // 3. 把 fragments + question 填进回答模板
        String prompt = buildPrompt(fragments, question);
        logger.info("prompt={}", prompt);
        // 4. 调 chat, 拿回文本
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    private String buildFragments(List<RetrievedChunk> chunks) {
        StringBuilder fragments = new StringBuilder();
        for (RetrievedChunk chunk : chunks) {
            fragments.append("[来源：")
                    .append(chunk.source()).append("#")
                    .append(chunk.heading()).append("]\n")
                    .append(" --- 得分是:[").append(chunk.score()).append("]\n")
                    .append(chunk.text()).append("\n\n");
        }
        return fragments.toString();
    }

    private String buildPrompt(String fragments, String question) {
        // 自己调措辞:片段按 score 降序放(检索已排序);要不要 few-shot 示例你自己试,理由写注释
        return """
                你是团队文档助手。仅根据以下文档片段回答问题:
                <fragments>
                %s
                </fragments>
                规则:
                1. 每个论断后面标注引用,如 [接口文档.md#登录接口]
                2. 文档中找不到的信息,回答「文档中没有相关内容」,不要推测
                3. 用中文回答,简洁
                问题:%s
                """.formatted(fragments, question);
    }
}
