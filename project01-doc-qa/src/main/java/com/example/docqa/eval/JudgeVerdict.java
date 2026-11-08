package com.example.docqa.eval;

/**
 * judge 的判分结果。rawResponse 留档 —— 判分要可审计。
 */
public record JudgeVerdict(int score, String reason, String rawResponse) {
}
