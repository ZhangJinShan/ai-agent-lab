package com.example.docqa.qa;

/** 把「调 chat 生成回答」抽成接口,测试时换成计数版 fake(断言 chat 没被调用) */
public interface AnswerGenerator {
    String generate(String systemPrompt, String userPrompt, String conversationId);
}
