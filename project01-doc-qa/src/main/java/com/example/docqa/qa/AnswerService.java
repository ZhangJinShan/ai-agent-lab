package com.example.docqa.qa;

import com.example.docqa.retriever.RetrievedChunk;
import com.example.docqa.retriever.Retriever;
import com.example.docqa.tool.Fragments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

/**
 * @author zhangjinshan
 * @description: 问答主链路：检索 → 拼引用片段 → 调用 chat → 带引用回答
 * @create: 2026/11/1
 **/
public class AnswerService {

    private final static Logger logger = LoggerFactory.getLogger(AnswerService.class);

    public static final String SYSTEM_TEMPLATE = """
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

    private final QueryRewriter queryRewriter;

    private final ChatMemory chatMemory;

    public AnswerService(Retriever retriever, AnswerGenerator answerGenerator, double minScore) {
       this(retriever, answerGenerator, minScore, "default");
    }

    public AnswerService(Retriever retriever, AnswerGenerator answerGenerator, double minScore, String conversationId) {
       this(retriever, answerGenerator, minScore, conversationId, null);
    }

    public AnswerService(Retriever retriever, AnswerGenerator answerGenerator, double minScore, String conversationId, QueryRewriter queryRewriter) {
        this(retriever, answerGenerator, minScore, conversationId, queryRewriter, null);
    }

    public AnswerService(Retriever retriever, AnswerGenerator answerGenerator, double minScore, String conversationId, QueryRewriter queryRewriter, ChatMemory chatMemory) {
        this.retriever = retriever;
        this.answerGenerator = answerGenerator;
        this.minScore = minScore;
        this.conversationId = conversationId;
        this.queryRewriter = queryRewriter;
        this.chatMemory = chatMemory;
    }

    public String answer(String question) {

        // 改写后的独立问题走检索，后续链路不变
        String effectiveQuestion = (queryRewriter != null) ? queryRewriter.rewrite(conversationId, question) : question;

        // 1. 检索 top-5
        List<RetrievedChunk> chunks = retriever.retrieve(effectiveQuestion);
        if (chunks.isEmpty()) {
            logger.info("No chunks found for question {}", effectiveQuestion);
            recordAbstain(question);
            return "文档中没有相关内容";
        }
        logger.info("question={}, top_1.source={}, top_1.score={}", effectiveQuestion, chunks.get(0).source() + "#" + chunks.get(0).heading(), chunks.get(0).score());
        if (chunks.get(0).score() < this.minScore) {
            logger.info("拒答: question={}, top_1.score={}", effectiveQuestion, chunks.get(0).score());
            recordAbstain(question);
            return "文档中没有相关内容";
        }
        // 2. 拼 fragments → 每块格式[来源: {source}#{heading}]\n{text}\n\n
        String fragments = Fragments.format(chunks);
        // 3. 把 fragments + question 填进回答模板
        String userPrompt = buildUserPrompt(fragments, effectiveQuestion);
        // 4. 调 chat, 拿回文本
        return answerGenerator.generate(SYSTEM_TEMPLATE, userPrompt, conversationId);
    }

    /** 拒答不进模型,但要补这一轮对话,否则后续追问的记忆链断;测试用 3 参构造器没接记忆,跳过 */
    private void recordAbstain(String question) {
        if (chatMemory == null) {
            return;
        }
        chatMemory.add(conversationId, List.of(
                new UserMessage(question),
                new AssistantMessage("文档中没有相关内容")));
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
