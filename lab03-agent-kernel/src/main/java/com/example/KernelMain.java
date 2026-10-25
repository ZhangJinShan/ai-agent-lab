package com.example;

import com.example.agent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

/**
 * @author zhangjinshan
 * @description: Kernel Main Method
 * @create: 2026/10/24
 **/
public class KernelMain {

    private static Logger logger = LoggerFactory.getLogger(KernelMain.class);

    public static void main(String[] args) {

        // 初始化配置管理器
        AgentConfig agentConfig = initAgentConfig();
        if (null == agentConfig) {
            logger.error("agentConfig init error");
        }

        // 初始化 llmClient
        LlmClient llmClient = initLlmClient(agentConfig);
        if (null == llmClient) {
            logger.error("llmClient init error");
            return;
        }

//        // 初始化工工具注册器
//        ToolRegistry registry = initToolRegistry();
//
//        // 初始化对话记忆器
//        ConversationMemory memory = new ConversationMemory(10);
//
//        // 调用 Agent
//        Agent agent = new Agent(llmClient, registry, agentConfig, memory);
//
//        Scanner scanner = new Scanner(System.in);
//        while (true) {
//            String userInput = scanner.nextLine();
//            if ("/new".equals(userInput)) {
//                agent.newConversation();
//                logger.info("已开启新会话....");
//                continue;
//            }
//            if ("/quit".equals(userInput)) {
//                logger.info("会话已结束，欢迎下次再来！");
//                break;
//            }
//            String response = agent.chat(userInput);
//            logger.info(response);
//        }

        for (int i = 0; i < 5; i++) {
            String response = llmClient.chat("你是一个智能助手,回答要简洁准确，如果不知道就直接回答不知道，不要捏造答案", "你是谁？");
            logger.info("response -> {}", response);
        }


    }

    private static LlmClient initLlmClient(AgentConfig agentConfig) {
        // 1. 对 classpath 下的 application.properties 文件
        Properties properties = getProperties();
        if (properties == null) return null;

        // 2. 读环境变量里的 api-key
        String apiKey = System.getenv("LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("请先设置环境变量 LLM_API_KEY(设置后需重启 IDE/终端)");
            return null;
        }
        return new LlmClient(properties.getProperty("llm.baseUrl"), properties.getProperty("llm.model"), apiKey, agentConfig);
    }

    /**
     * 初始化工具注册器
     *
     * @return
     */
    private static ToolRegistry initToolRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(
                new Tool(
                        "get_weather",
                        "查询指定城市的当前天气",
                        "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"城市名\"}},\"required\":[\"city\"]}",
                        ToolFunction::getWeather)
        );
        registry.register(
                new Tool(
                        "calculate",
                        "计算用户提供的表达式的结果并返回",
                        "{\"type\":\"object\",\"properties\":{\"expression\":{\"type\":\"string\",\"description\":\"表达式\"}},\"required\":[\"expression\"]}",
                        ToolFunction::calculate)
        );
        return registry;
    }

    private static AgentConfig initAgentConfig() {
        Properties properties = getProperties();
        if (properties == null) return null;

        return AgentConfig.builder()
                .systemPrompt("你是一个智能助手,回答要简洁准确，如果不知道就直接回答不知道，不要捏造答案")
                .temperature(Double.parseDouble(properties.getProperty("llm.temperature")))
                .build();
    }

    private static Properties getProperties() {
        Properties properties = new Properties();
        try (InputStream in = KernelMain.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) {
                System.err.println("找不到 application.properties,检查文件是否在 src/main/resources 下");
                return null;
            }
            properties.load(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return properties;
    }
}
