package com.example;

import com.example.agent.AgentConfig;
import com.example.agent.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * @author zhangjinshan
 * @description: Kernel Main Method
 * @create: 2026/10/24
 **/
public class KernelMain {

    private static Logger logger = LoggerFactory.getLogger(KernelMain.class);

    public static void main(String[] args) {
        // 初始化 llmClient
        LlmClient llmClient = initLlmClient();
        if (null == llmClient) {
            logger.error("llmClient init fail");
            return;
        }
        for (int i = 0; i < 3; i++) {
            String response = llmClient.chat("你是一个简洁的助手", "你好,请用一句话介绍你自己");
            logger.info("response -> {}", response);
        }
    }

    private static LlmClient initLlmClient() {
        // 1. 对 classpath 下的 application.properties 文件
        Properties properties = new Properties();
        try (InputStream in = KernelMain.class.getClassLoader().getResourceAsStream("application.properties")){
            if (in == null) {
                System.err.println("找不到 application.properties,检查文件是否在 src/main/resources 下");
                return null;
            }
            properties.load(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 2. 读环境变量里的 api-key
        String apiKey = System.getenv("LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("请先设置环境变量 LLM_API_KEY(设置后需重启 IDE/终端)");
            return null;
        }

        // 3. 初始化 AgentConfig
        AgentConfig agentConfig = AgentConfig.builder()
                .temperature(Double.parseDouble(properties.getProperty("llm.temperature")))
                .build();

        return new LlmClient(properties.getProperty("llm.baseUrl"), properties.getProperty("llm.model"), apiKey, agentConfig);
    }
}
