package com.example.llm;

import java.io.InputStream;
import java.util.Properties;

/**
 * @author zhangjinshan
 * @description: Main Test
 * @create: 2026/10/11
 **/
public class Main {

    public static void main(String[] args) {
        // 1. 对 classpath 下的 application.properties 文件
        Properties properties = new Properties();
        try (InputStream in = Main.class.getClassLoader().getResourceAsStream("application.properties")){
            if (in == null) {
                System.err.println("找不到 application.properties,检查文件是否在 src/main/resources 下");
                return;
            }
            properties.load(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 2. 读环境变量里的 api-key
        String apiKey = System.getenv("LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("请先设置环境变量 LLM_API_KEY(设置后需重启 IDE/终端)");
            return;
        }

        // 3. 构造 client —— 参数不对会抛 IllegalArgumentException
        LlmClient llmClient = new LlmClient(properties.getProperty("llm.baseUrl"), properties.getProperty("llm.model"), apiKey);

        // 4. 打印验证(baseUrl 打印出来没问题,apiKey 千万不要打印!)
        System.out.println("配置加载成功!");
        System.out.println("  baseUrl = " + properties.getProperty("llm.baseUrl"));
        System.out.println("  model   = " + properties.getProperty("llm.model"));
        System.out.println("  apiKey  = 已加载(" + apiKey.length() + " 个字符)");


//        String answer = llmClient.chat("你是一个简洁的助手", "你好,请用一句话介绍你自己");
//
//        System.out.println("模型回复: " + answer);
    }
}
