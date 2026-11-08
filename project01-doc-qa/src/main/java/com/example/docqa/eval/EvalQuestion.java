package com.example.docqa.eval;

/**
 * 一道评估题。
 * cite = 期望引用的出处("接口文档.md#登录接口");abstain 题为 null。
 */
public record EvalQuestion(int id, String question, String expected, String type, String cite) {
}
