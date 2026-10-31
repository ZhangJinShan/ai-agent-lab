package com.example.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.concurrent.atomic.LongAdder;

/**
 * @author zhangjinshan
 * @description: Token 账本:线程安全(虚拟线程时代的好习惯),跨多轮累加
 * @create: 2026/10/25
 **/
public class TokenUsage {
    // 输入 token
    private final LongAdder promptTokens = new LongAdder();

    // 输出 token
    private final LongAdder completionTokens = new LongAdder();

    private final LongAdder calls = new LongAdder();

    /** 从响应 JSON 的 usage 节点提取并累加。*/
    public void record(JsonNode usageNode) {
        // 空节点时，不记成本
        if (usageNode.isMissingNode() || usageNode.isNull()) {
            return;
        }
        promptTokens.add(usageNode.path("prompt_tokens").asLong());
        completionTokens.add(usageNode.path("completion_tokens").asLong());
        calls.increment(); // 记一笔 = 一次调用
    }

    public long totalTokens() { return promptTokens.sum() + completionTokens.sum(); }

    public long calls(){
        return calls.sum(); // 汇总打印用
    }

    /**
     * 按单价算钱。
     * pricePerMillionPrompt / pricePerMillionCompletion 传入(单位:元/百万 token)
     * 花费 = prompt/1_000_000 * 单价1 + completion/1_000_000 * 单价2
     * 单价从供应商价格页查(DeepSeek/Qwen 都是元/百万 token 计价),写进配置而不是硬编码
     */
    public String bill(double pricePerMillionPrompt, double pricePerMillionCompletion) {
        double promptBill = promptTokens.doubleValue() / 1_000_000 * pricePerMillionPrompt;
        double completionBill = completionTokens.doubleValue() / 1_000_000 * pricePerMillionCompletion;
        double totalBill = promptBill + completionBill;
        return String.format("%.2f", totalBill) ;
    }

    public long promptTokens(){
        return promptTokens.longValue();
    }

    public long completionTokens(){
        return completionTokens.longValue();
    }

    public void reset(){
        promptTokens.reset();
        completionTokens.reset();
        calls.reset();
    }
}
