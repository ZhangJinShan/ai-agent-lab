package com.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();

    public ToolRegistry registry(Tool tool){
        tools.put(tool.name(), tool);
        return this;
    }

    /**
     * 生成 OpenAI tools 字段需要的 JSON 数组字符串
     * @return
     */
    public String toToolJson(){



        return "";
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
