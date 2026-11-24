package com.example.docqa.eval;

import java.util.List;

/**
 * @author zhangjinshan
 * @description: 三个指标汇总。分子分母的定义见类注释 —— 这是评估报告的法律文本。
 * @create: 2026/11/24
 **/
public class Metrics {

    private static final String ABSTAIN_TEXT = "文档中没有相关内容";

    public static boolean isAbstainAnswer(String answer){
        // TODO: 判定逻辑(contains?还是集合匹配多个变体?选一个写注释)
        return false;
    }

    /** 计算三个指标(输入:每题的判定结果列表) */
    public static Report summarize(List<ItemResult> items) {
        // TODO: 按 3.1 的定义算;注意:
        //   - 引用准确率分母可能为 0(全是 abstain)→ 除零防御,返回 N/A
        //   - 拒答正确率拆两个子数(abstain 拒答率 / 误拒率)一起放进 Report
        return null;
    }


    /** 一题的判定结果（跑批时逐题填） **/
    public record ItemResult(int id,
                             String type,
                             int judgeScore,
                             boolean judgeError,
                             boolean citeHit,
                             boolean citeRequired,
                             boolean abstained){}


    /** 报告指标 (打印和 baseline.md 共用)**/
    public record Report(double answerAccuracy,
                         double citationAccuracy,
                         double abstainAccuracy,
                         double abstainRecall,
                         double falseAbstainRate,
                         int judgeErrors){}

}
