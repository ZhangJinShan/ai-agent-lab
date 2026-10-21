package com.example;

import com.example.agent.Tool;
import com.example.agent.ToolRegistry;

import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        ToolRegistry registry = new ToolRegistry();

        registry.register(new Tool(
                "get_weather",
                "查询指定城市的当前天气",
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"城市名\"}},\"required\":[\"city\"]}",
                argsJson -> "晴,25°C"));

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", "北京今天天气怎么样?"));



    }
}
