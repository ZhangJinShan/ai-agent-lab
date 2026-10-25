package com.example.agent;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PromptTemplateTest {

    /**
     * 测试案例:正常渲染——占位符全部替换成对应值
     */
    @Test
    void testRenderReplacesAllPlaceholders() {
        // given: 模板有两个占位符,values 全都给全
        PromptTemplate template = new PromptTemplate("你是 {userName} 的助手,今天是 {date},回答要简洁");
        String today = LocalDate.now().toString();   // 期望值动态拼,不能写死日期(写死明天就红)

        // when
        String render = template.render(Map.of("userName", "老张", "date", today));

        // then
        assertEquals("你是 老张 的助手,今天是 " + today + ",回答要简洁", render);
    }

    /**
     * 测试案例:缺失占位符抛异常,且一次报出所有缺失 key(策略 1)
     */
    @Test
    void testRenderThrowsAndListsAllMissingKeys() {
        // given: 模板要 3 个变量,values 只给了 1 个,缺 date、city
        PromptTemplate template = new PromptTemplate("你是 {userName} 的助手,今天是 {date},城市 {city}");

        // when/then: 必须抛 IllegalArgumentException(而不是静默保留 {date})
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> template.render(Map.of("userName", "老张")));

        // 错误消息要把缺失的 key 全列出来,别修一个再撞下一个
        assertTrue(ex.getMessage().contains("date"), "错误消息应包含缺失的 key: date,实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("city"), "错误消息应包含缺失的 key: city,实际: " + ex.getMessage());
    }

    /**
     * 测试案例:values 里多余的 key 静默忽略(策略 2,方便一套 values 喂多个模板)
     */
    @Test
    void testRenderIgnoresExtraKeys() {
        // given: values 里多给了模板用不到的 unusedKey
        PromptTemplate template = new PromptTemplate("你好 {userName}");

        // when: 多余 key 不该抛异常
        String render = template.render(Map.of("userName", "老张", "unusedKey", "随便"));

        // then: 正常渲染,多余 key 不影响结果
        assertEquals("你好 老张", render);
    }

    /**
     * 测试案例:values 的值里含 {xxx} 不被二次替换(策略 3,单次扫描)
     */
    @Test
    void testRenderDoesNotReplacePlaceholdersInsideValues() {
        // given: value 本身长得像占位符 {date}
        PromptTemplate template = new PromptTemplate("内容: {payload}");

        // when
        String render = template.render(Map.of("payload", "{date}"));

        // then: 必须原样输出 {date},不能被当成占位符再替换一遍
        assertEquals("内容: {date}", render);
    }

    /**
     * 测试案例:无占位符的纯文本模板 + 空 map → 原样返回(Agent.chat 里 systemPrompt 的常见用法)
     */
    @Test
    void testRenderWithoutPlaceholdersReturnsAsIs() {
        // given: 纯文本,没有任何 {key} 占位符
        PromptTemplate template = new PromptTemplate("你是一个智能助手,回答要简洁");

        // when: 空 map 渲染
        String render = template.render(Map.of());

        // then: 原样返回
        assertEquals("你是一个智能助手,回答要简洁", render);
    }
}
