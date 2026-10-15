package com.example.agent;

import java.util.function.Function;

/**
 * 工具抽象:声明(name/description/schema)和实现(handler)绑在一起。
 */
public record Tool(
        String name,
        String description,
        String parametersJsonSchema,  // JSON Schema 字符串
        Function<String, String> handler    // 入参:JSON 字符串;出参:结果字符串
) {
}
