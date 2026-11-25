package com.example.docqa.eval;

import java.util.List;

/**
 * @author zhangjinshan
 * @description: 三个指标汇总。分子分母的定义见类注释 —— 这是评估报告的法律文本。
 * @create: 2026/11/24
 **/
public class Metrics {

    private static final String ABSTAIN_TEXT = "文档中没有相关内容";

    public static boolean isAbstainAnswer(String answer) {
        return answer != null && answer.contains(ABSTAIN_TEXT);
    }

    /**
     * 计算三个指标(输入:每题的判定结果列表)
     */
    public static Report summarize(List<ItemResult> items) {
        List<ItemResult> rows = items == null ? List.of() : items;
        int totalCount = rows.size();
        int answerCorrect = 0;
        int judgeErrors = 0;
        int citeRequired = 0;
        int citeHit = 0;
        int abstainTotal = 0;
        int abstainHit = 0;
        int falseAbstain = 0;
        int correctNonAbstain = 0;

        for (ItemResult r : rows) {
            if (r.judgeError()) {
                judgeErrors++;
            } else if (r.judgeScore() == 1) {
                answerCorrect++;
            }

            if (r.citeRequired()) {
                citeRequired++;
                if (r.citeHit()) {
                    citeHit++;
                }
            }

            if ("abstain".equals(r.type())) {
                abstainTotal++;
                if (r.abstained()) {
                    abstainHit++;
                }
            } else if (r.abstained()) {
                falseAbstain++;
            } else {
                correctNonAbstain++;
            }
        }

        Double answerAccuracy = totalCount == 0 ? null : (double) answerCorrect / totalCount;
        Double citationAccuracy = citeRequired == 0 ? null : (double) citeHit / citeRequired;
        Double abstainAccuracy = totalCount == 0 ? null
                : (double) (abstainHit + correctNonAbstain) / totalCount;
        Double abstainRecall = abstainTotal == 0 ? null : (double) abstainHit / abstainTotal;
        Double falseAbstainRate = (totalCount - abstainTotal) == 0 ? null
                : (double) falseAbstain / (totalCount - abstainTotal);

        return new Report(answerAccuracy, citationAccuracy, abstainAccuracy,
                abstainRecall, falseAbstainRate, judgeErrors);
    }


    /**
     * 一题的判定结果（跑批时逐题填）
     **/
    public record ItemResult(int id,
                             String type,
                             int judgeScore,
                             boolean judgeError,
                             boolean citeHit,
                             boolean citeRequired,
                             boolean abstained) {
    }


    /**
     * 报告指标 (打印和 baseline.md 共用); 率字段为 null 表示 N/A（分母为 0）
     **/
    public record Report(Double answerAccuracy,
                         Double citationAccuracy,
                         Double abstainAccuracy,
                         Double abstainRecall,
                         Double falseAbstainRate,
                         int judgeErrors) {
    }

}
