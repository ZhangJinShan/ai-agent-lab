package com.example.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();

    public ToolRegistry register(Tool tool){
        tools.put(tool.name(), tool);
        return this;
    }

    /**
     * 生成 OpenAI tools 字段需要的 JSON 数组字符串
     * @return
     */
    public String toToolJson() throws JsonProcessingException {
        ArrayNode root = mapper.createArrayNode();
        tools.forEach((s, tool) -> {
            ObjectNode node = mapper.createObjectNode();
            node.put("type", "function");
            Map<String, String> functionMap = new HashMap<>();
            functionMap.put("name", s);
            functionMap.put("description", tool.description());
            functionMap.put("parameters", tool.parametersJsonSchema());
            node.put("function", functionMap.toString());
            root.add(node);
        });

        return mapper.writeValueAsString(root);
    }

    /**
     * 执行工具:任何异常都转成错误文本返回,绝不让循环崩掉
     * @param name
     * @param argsJson
     * @return
     */
    public String execute(String name, String argsJson){
        Tool tool = tools.get(name);
        if (null == tool) {
            return "工具不存在: " + name + ",可用工具: " + tools.keySet();
        }
        try {
            return tool.handler().apply(argsJson);
        } catch (Exception e) {
            return "工具执行出错: " + e.getMessage();
        }

    }

    public List<Tool> all(){
        return List.copyOf(tools.values());
    }


    public static void main(String[] args) throws JsonProcessingException {
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new Tool(
                "get_weather",
                "查询指定城市的当前天气",
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"城市名\"}},\"required\":[\"city\"]}",
                argsJson -> "晴,25°C"
        ));

        System.out.println(toolRegistry.toToolJson());
    }
}
