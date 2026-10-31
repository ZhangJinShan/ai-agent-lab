package com.example.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author zhangjinshan
 * @description: 提示词模板
 * @create: 2026/10/25
 **/
public class PromptTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final String template;

    public PromptTemplate(String template) {
        this.template = template;
    }

    /**
     * 渲染策略(明确选择,不搞隐式行为):
     *   1. 模板里的 {key} 在 values 中缺失 → 抛 IllegalArgumentException,一次性报出所有缺失的 key
     *      (不静默保留 {key}、不置空——让模型看到占位符原文比报错更糟)
     *   2. values 里多余的 key(模板没用到)→ 静默忽略(方便一套 values 喂多个模板)
     *   3. values 的值里若包含 {xxx},不视为占位符,原样输出(单次扫描,不做二次替换)
     */
    public String render(Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        List<String> missing = new ArrayList<>();
        while (m.find()) {
            String key = m.group(1);
            String value = values.get(key); // 括号里面的 key, 如 userName
            if (value == null) {
                missing.add(key); // 攒起来，最后一起报，别修一个再撞下个
                value = m.group(); // 先原样放，渲染完统一抛
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("模板缺少变量: " + missing);
        }
        return sb.toString();
    }
}
