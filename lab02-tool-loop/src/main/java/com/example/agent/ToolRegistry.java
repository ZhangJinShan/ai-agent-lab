package com.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {

    private final ObjectMapper mapper = new ObjectMapper();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool){
        tools.put(tool.name(), tool);
    }

    /**
     * 生成 OpenAI tools 字段需要的 JSON 数组字符串
     * @return
     */
    public String toToolsJson(){
        try {
            List<ToolItem> toolItems = new ArrayList<>();
            for (Map.Entry<String, Tool> entry : tools.entrySet()) {
                Tool tool = entry.getValue();
                ToolFunction toolFunction = new ToolFunction(tool.name(), tool.description(), mapper.readTree(tool.parametersJsonSchema()));
                ToolItem toolItem = new ToolItem("function", toolFunction);
                toolItems.add(toolItem);
            }
            return mapper.writeValueAsString(toolItems);
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

    @Data
    static class ToolItem{
        private String type;
        private ToolFunction function;

        public ToolItem(String type, ToolFunction function) {
            this.type = type;
            this.function = function;
        }
    }

    @Data
    static class ToolFunction{
        private String name;
        private String description;
        private Object parameters;

        public ToolFunction(String name, String description, Object parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }
    }
}
