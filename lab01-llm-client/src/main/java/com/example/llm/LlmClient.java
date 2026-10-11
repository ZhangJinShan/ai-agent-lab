package com.example.llm;

/**
 * @author zhangjinshan
 * @description: Llm Invoke Client
 * @create: 2026/10/11
 **/
public class LlmClient {

    private final String baseUrl;

    private final String model;

    private final String apiKey;

    public LlmClient(String baseUrl, String model, String apiKey) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl 不能为空");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey 不能为空");
        }

        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
    }
}
