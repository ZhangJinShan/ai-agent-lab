package com.example.docqa.qa;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.UserMessage;

import static org.junit.jupiter.api.Assertions.*;

public class QueryRewriterTest {

    private static final String CID = "c1";

    private ChatMemory memoryWith(String... history) {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
        for (String text : history) {
            memory.add(CID, new UserMessage(text));
        }
        return memory;
    }

    @Test
    void 两连问_改写成独立问题_历史和术语要求都进请求() {
        QueryRewriter rw = new QueryRewriter(memoryWith("登录接口的限流是多少?"), (system, user) -> {
            assertTrue(system.contains("保留"), "改写 prompt 应要求保留术语");
            assertTrue(user.contains("登录接口的限流是多少?"), "历史应进改写请求");
            return "登录接口的密码错误阈值是多少?";
        });
        assertEquals("登录接口的密码错误阈值是多少?", rw.rewrite(CID, "那密码错误呢?"));
    }

    @Test
    void 首轮无历史_不调chat_原样返回() {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
        QueryRewriter rw = new QueryRewriter(memory, (s, u) -> {
            fail("首轮不该调 chat");
            return null;
        });
        assertEquals("登录接口的限流是多少?", rw.rewrite(CID, "登录接口的限流是多少?"));
    }

    @Test
    void 空结果_兜底回原问题() {
        QueryRewriter rw = new QueryRewriter(memoryWith("登录接口的限流是多少?"), (s, u) -> "   ");
        assertEquals("", rw.rewrite(CID, "那密码错误呢?"));
    }

    @Test
    void 异常_兜底回原问题() {
        QueryRewriter rw = new QueryRewriter(memoryWith("登录接口的限流是多少?"), (s, u) -> {
            throw new RuntimeException("超时");
        });
        assertEquals("那密码错误呢?", rw.rewrite(CID, "那密码错误呢?"));
    }
}
