package com.example.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {

    Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();

    public void register(Tool tool){
        tools.put(tool.name(), tool);
    }

    /**
     * 生成 OpenAI tools 字段需要的 JSON 数组字符串
     * @return
     */
    public String toToolsJson(){
        try {
            ArrayNode root = mapper.createArrayNode();
            for (Map.Entry<String, Tool> entry : tools.entrySet()) {
                Tool tool = entry.getValue();
                ObjectNode item = mapper.createObjectNode();
                item.put("type", "function");

                // function
                ObjectNode objectNode = mapper.createObjectNode();
                objectNode.put("name", tool.name());
                objectNode.put("description", tool.description());

                // parameter 对象
                JsonNode jsonNode = mapper.readTree(tool.parametersJsonSchema());
                objectNode.putIfAbsent("parameters", jsonNode);

                item.putIfAbsent("function", objectNode);
                root.add(item);
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            logger.error("Error while tool json", e);
            throw new RuntimeException(e);
        }
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
}
