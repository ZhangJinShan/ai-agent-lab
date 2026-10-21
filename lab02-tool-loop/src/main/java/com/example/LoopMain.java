package com.example;

import com.example.agent.Tool;
import com.example.agent.ToolRegistry;
import com.example.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class LoopMain {

    private static final Logger logger = LoggerFactory.getLogger(LoopMain.class);

    public static void main(String[] args) {

        ToolRegistry registry = new ToolRegistry();

        registry.register(new Tool(
                "get_weather",
                "查询指定城市的当前天气",
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"城市名\"}},\"required\":[\"city\"]}",
                argsJson -> "晴,25°C"));

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", "北京今天天气怎么样?"));

        LlmClient llmClient = initLlmClient();
        if (null == llmClient) {
            logger.error("llmClient init fail");
            return;
        }
        String toolsJson = registry.toToolsJson();
        logger.info("toolsJson: {}", toolsJson);

        String raw = llmClient.chatCompletions(messages, toolsJson);
        System.out.println(raw);
    }


    private static LlmClient initLlmClient() {

        // 1. 对 classpath 下的 application.properties 文件
        Properties properties = new Properties();
        try (InputStream in = LoopMain.class.getClassLoader().getResourceAsStream("application.properties")){
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

        // 3. 构造 client —— 参数不对会抛 IllegalArgumentException
        return new LlmClient(properties.getProperty("llm.baseUrl"), properties.getProperty("llm.model"), apiKey);
    }
}
