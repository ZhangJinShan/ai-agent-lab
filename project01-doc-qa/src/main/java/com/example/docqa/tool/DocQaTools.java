package com.example.docqa.tool;


import com.example.docqa.retriever.RetrievedChunk;
import com.example.docqa.retriever.Retriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * @author zhangjinshan
 * @description: agentic RAG 入口:把检索声明成工具,由模型决定要不要查
 * @create: 2026/11/6
 **/
public class DocQaTools {

    private static final Logger log = LoggerFactory.getLogger(DocQaTools.class);

    private final Retriever retriever;

    private final double minScore;

    public DocQaTools(Retriever retriever, double minScore) {
        this.retriever = retriever;
        this.minScore = minScore;
    }

    /**
     * 关于  description 的说明
     * 工具是什么 / 返回什么：查询团队文档知识库(接口文档、配置说明、故障手册)，返回代来源和相似度分数的片段
     * 什么时候该调：当问题涉及接口行为、配置项默认值或范围，错误码、故障处理步骤时使用
     * 什么时候不调：闲聊、常识问答、身份介绍等与团队文档无关的问题直接作答，不要调用工具
     * 可多次调用: 问题设计多个不同主题或需要对比时，可用不同关键词分别检索多次，再综合回答
     * 保留话术: 输入检索关键词或完整问题,保留原问题中的配置项名和错误码,不要同义替换
     * 参数怎么填：检索关键词或完整问句; 对比类问题每次只传一个主题;保留 max-active、read-timeout、USER_LOCKED 等原始术语, 不要改写成中文描述
     * @param query: 查询参数
     * @return 范文检索结果
     */
    @Tool(description = "查询团队文档知识库(接口文档、配置说明、故障手册)，返回代来源和相似度分数的片段。" +
            "当问题涉及接口行为、配置项默认值或范围，错误码、故障处理步骤时使用; " +
            "闲聊、常识问答、身份介绍等与团队文档无关的问题直接作答，不要调用工具; " +
            "问题设计多个不同主题或需要对比时，可用不同关键词分别检索多次，再综合回答; " +
            "输入检索关键词或完整问题,保留原问题中的配置项名和错误码,不要同义替换")
    public String searchDocs(@ToolParam(description = "检索关键词或完整问句; 对比类问题每次只传一个主题; " +
            "保留 max-active、read-timeout、USER_LOCKED 等原始术语, 不要改写成中文描述") String query){
        try {
            List<RetrievedChunk> chunks = retriever.retrieve(query);
            if (chunks.isEmpty() || chunks.get(0).score() < minScore) {
                log.info("searchDocs 无相关内容: query={}", query);
                return "知识库中没有相关内容";
            }

            log.info("searchDocs 命中 {} 块: query={}, top_1.score={}", chunks.size(), query, chunks.get(0).score());
            return Fragments.format(chunks);
        } catch (Exception e) {
            log.warn("searchDocs 异常,回错误文本: query={}", query, e);
            return "知识库中没有相关内容";
        }
    }
}
