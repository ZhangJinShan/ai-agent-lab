package com.example.docqa.qa;

import com.example.docqa.retriever.RetrievedChunk;
import com.example.docqa.retriever.Retriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author zhangjinshan
 * @description: 问答主链路：检索 → 拼引用片段 → 调用 chat → 带引用回答
 * @create: 2026/11/1
 **/
public class AnswerService {

    private final static Logger logger = LoggerFactory.getLogger(AnswerService.class);

    private static final String SYSTEM_TEMPLATE = """
            你是团队文档助手。
            规则:
            1. 仅根据提供的文档片段回答问题,不要使用片段以外的知识
            2. 每个论断后面标注引用,如 [接口文档.md#登录接口]
            3. 文档中找不到的信息,回答「文档中没有相关内容」,不要推测
            4. 用中文回答,简洁
            """;

    private final Retriever retriever;

    private final AnswerGenerator answerGenerator;

    private final double minScore;

    // 多轮记忆的会话标识;默认 "default" 即让同一 AnswerService 实例共享上下文
    private final String conversationId;

    public AnswerService(Retriever retriever, AnswerGenerator answerGenerator, double minScore) {
       this(retriever, answerGenerator, minScore, "default");
    }

    public AnswerService(Retriever retriever, AnswerGenerator answerGenerator, double minScore, String conversationId) {
        this.retriever = retriever;
        this.answerGenerator = answerGenerator;
        this.minScore = minScore;
        this.conversationId = conversationId;
    }

    public String answer(String question) {
        // 1. 检索 top-5
        List<RetrievedChunk> chunks = retriever.retrieve(question);
        if (chunks.isEmpty()) {
            logger.info("No chunks found for question {}", question);
            return "文档中没有相关内容";
        }
        logger.info("question={}, top_1.source={}, top_1.score={}", question, chunks.get(0).source() + "#" + chunks.get(0).heading(), chunks.get(0).score());
        if (chunks.get(0).score() < this.minScore) {
            logger.info("拒答: question={}, top_1.score={}", question, chunks.get(0).score());
            return "文档中没有相关内容";
        }
        // 2. 拼 fragments → 每块格式[来源: {source}#{heading}]\n{text}\n\n
        String fragments = buildFragments(chunks);
        // 3. 把 fragments + question 填进回答模板
        String userPrompt = buildUserPrompt(fragments, question);
        // 4. 调 chat, 拿回文本
        return answerGenerator.generate(SYSTEM_TEMPLATE, userPrompt, conversationId);
    }

    private String buildFragments(List<RetrievedChunk> chunks) {
        StringBuilder fragments = new StringBuilder();
        for (RetrievedChunk chunk : chunks) {
            fragments.append("[来源：").append(chunk.source()).append("#").append(chunk.heading()).append("]\n")
                    .append(" --- 得分是:[").append(chunk.score()).append("]\n")
                    .append(chunk.text()).append("\n\n");
        }
        return fragments.toString();
    }

    private String buildUserPrompt(String fragments, String question) {
        // 自己调措辞:片段按 score 降序放(检索已排序);要不要 few-shot 示例你自己试,理由写注释
        return """
                <fragments>
                %s
                </fragments>
                问题:%s
                """.formatted(fragments, question);
    }
}
