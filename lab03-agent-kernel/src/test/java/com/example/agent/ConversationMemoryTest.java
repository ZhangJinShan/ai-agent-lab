package com.example.agent;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConversationMemoryTest {

    /**
     * 清单 1:加 30 条普通消息,窗口 20 → 剩 20 条,丢的是最早的
     */
    @Test
    void 测试1_加30条普通消息_窗口20_剩20条且丢最早的() {
        // given: 窗口大小 20
        ConversationMemory memory = new ConversationMemory(20);

        // when: 连加 30 条普通消息(msg-0 ~ msg-29),add 内部每条都会触发 trim
        for (int i = 0; i < 30; i++) {
            memory.add(msg("user", "msg-" + i));
        }

        // then: 只剩 20 条,丢掉的是最早的 msg-0 ~ msg-9
        List<Map<String, Object>> messages = memory.messages();
        assertEquals(20, messages.size(), "窗口 20,加 30 条后应只剩 20 条");
        assertEquals("msg-10", messages.get(0).get("content"),
                "留下的最早一条应是 msg-10(msg-0~msg-9 已丢弃)");
        assertEquals("msg-29", messages.get(messages.size() - 1).get("content"),
                "最新一条必须还在");
    }

    /**
     * 清单 2:system 消息永远在,不管加多少条
     */
    @Test
    void 测试2_system消息永远保留_不管加多少条() {
        // given: 窗口 20,开头一条 system(人设)
        ConversationMemory memory = new ConversationMemory(20);
        memory.add(msg("system", "你是一个智能助手"));

        // when: 再加 30 条普通消息,把窗口冲爆
        for (int i = 0; i < 30; i++) {
            memory.add(msg("user", "msg-" + i));
        }

        // then: system 还在且在最前;它不占窗口名额,总数 = 1 system + 20 普通 = 21
        List<Map<String, Object>> messages = memory.messages();
        assertEquals(21, messages.size(), "system 不占窗口:1 条 system + 20 条普通 = 21 条");
        assertEquals("system", messages.get(0).get("role"),
                "system 必须永远保留,且排在最前面");
    }

    /**
     * 清单 3:assistant(tool_calls)+tool 一组不会被裁一半
     * 造一个「按条裁会出孤儿、按组裁才正确」的现场:
     *   裁剪要丢 2 条最早的普通消息(q1、a1),整组工具消息必须完整活下来。
     *   如果 trim 只按条数裁,会把组头 assistant(tool_calls) 裁掉、留下孤儿 tool。
     */
    @Test
    void 测试3_assistant带tool_calls和tool结果_一组不被裁一半() {
        // given: 窗口 3;历史:system + q1 + a1 + [组头 assistant(tool_calls) + 组员 tool] + a2
        ConversationMemory memory = new ConversationMemory(3);
        memory.add(msg("system", "你是一个智能助手"));
        memory.add(msg("user", "q1"));
        memory.add(msg("assistant", "a1"));
        memory.add(toolCallsMsg("让我查一下天气"));   // 组头:assistant 消息带 tool_calls
        memory.add(msg("tool", "晴,25°C"));          // 组员:工具执行结果
        memory.add(msg("assistant", "a2"));          // 最终答案

        // when: 非 system 共 5 条 > 窗口 3,trim 丢最早的 q1、a1 两条普通消息后停下

        // then: 剩 4 条 [system, assistant(tool_calls), tool, a2] —— 整组完整
        List<Map<String, Object>> messages = memory.messages();
        assertEquals(4, messages.size(), "system + 整组工具消息 + 最终答案 = 4 条");
        assertEquals("system", messages.get(0).get("role"), "system 必须还在");
        assertNotNull(messages.get(1).get("tool_calls"), "组头 assistant(tool_calls) 必须还在,不能被单独裁掉");
        assertEquals("tool", messages.get(2).get("role"), "tool 结果必须紧跟组头,一组共存亡");
        assertEquals("a2", messages.get(3).get("content"), "最新一条必须还在");

        // 兜底不变量:任何 tool 消息的前一条必须是带 tool_calls 的 assistant(不许有孤儿 tool)
        for (int i = 1; i < messages.size(); i++) {
            if ("tool".equals(messages.get(i).get("role"))) {
                assertNotNull(messages.get(i - 1).get("tool_calls"),
                        "第 " + i + " 条是孤儿 tool 消息:它的组头被裁掉了(一组被裁一半)");
            }
        }
    }

    // ---------- 构造消息的小工具 ----------

    private static Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    /** 组头消息:assistant + tool_calls(trim 的 groupEnd 靠 tool_calls 非空识别组) */
    private static Map<String, Object> toolCallsMsg(String content) {
        Map<String, Object> m = msg("assistant", content);
        m.put("tool_calls", List.of(Map.of("id", "call_1", "function", "get_weather")));
        return m;
    }
}
