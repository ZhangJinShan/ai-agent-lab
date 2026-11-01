package com.example.docqa.qa;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author zhangjinshan
 * @description: 计数版 fake 记录：记录 generator 被调用次数 + 捕获最后一次 prompt
 * @create: 2026/11/1
 **/
public class FakeAnswerGenerator implements AnswerGenerator {

    private final AtomicInteger callCounter = new AtomicInteger(0);

    private String answer = "假回答";

    private String lastPrompt = "";

    @Override
    public String generate(String prompt) {
        callCounter.incrementAndGet();
        lastPrompt = prompt;
        return answer;
    }

    public int callCount(){
        return callCounter.get();
    }

    public String lastPrompt(){
        return lastPrompt;
    }

    public void setAnswer(String answer){
        this.answer = answer;
    }
}
