package com.example.docqa.qa;

import com.example.docqa.retriever.FakeRetriever;
import com.example.docqa.retriever.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author zhangjinshan
 * @description: 主测试
 * @create: 2026/11/1
 **/
public class AnswerServiceTest {

    private static final double MIN_SCORE = 0.3; // 测试里写死,与真实配置解耦

    // ===== 5 个「有答案」:fake 给高分相关块 =====

    @Test
    void 登录限流_走回答路径_含来源_调了chat() {
        var retriever = new FakeRetriever(Map.of(
                "限流", List.of(new RetrievedChunk(
                        "登录接口限流为 100 次/分钟", "接口文档.md", "登录接口", 0.82))
        ));
        var fakeChat = new FakeAnswerGenerator();
        fakeChat.setAnswer("接口限流 100 次/分钟 [接口文档.md#登录接口]");
        var service = new AnswerService(retriever, fakeChat, MIN_SCORE);

        String result = service.answer("登录接口的限流是多少?");

        assertFalse(result.contains("文档中没有相关内容"), "有答案不应拒答");
        assertEquals(1, fakeChat.callCount(), "应调用一次 chat");
        assertTrue(fakeChat.lastPrompt().contains("接口文档.md#登录接口"), "fragments 应带来源");
        assertTrue(fakeChat.lastPrompt().contains("登录接口的限流是多少?"), "模板应含问题原文");
    }

    @Test
    void 密码锁定_走回答路径_含来源() {
        var retriever = new FakeRetriever(Map.of(
                "锁定", List.of(new RetrievedChunk(
                        "密码错 5 次锁定 30 分钟", "接口文档.md", "密码策略", 0.78))
        ));
        var fakeChat = new FakeAnswerGenerator();
        var service = new AnswerService(retriever, fakeChat, MIN_SCORE);

        String result = service.answer("密码错几次会锁定?");

        assertFalse(result.contains("文档中没有相关内容"));
        assertEquals(1, fakeChat.callCount());
        assertTrue(fakeChat.lastPrompt().contains("接口文档.md#密码策略"));
    }

    @Test
    void 改密码限流_走回答路径() {
        var retriever = new FakeRetriever(Map.of(
                "改密码", List.of(new RetrievedChunk(
                        "修改密码每分钟最多 3 次", "接口文档.md", "密码策略", 0.75))
        ));
        var service = new AnswerService(retriever, new FakeAnswerGenerator(), MIN_SCORE);
        assertFalse(service.answer("改密码有限流吗?").contains("文档中没有相关内容"));
    }

    @Test
    void readTimeout默认_走回答路径() {
        var retriever = new FakeRetriever(Map.of(
                "read-timeout", List.of(new RetrievedChunk(
                        "read-timeout 默认 60 秒", "配置说明.md", "超时配置", 0.71))
        ));
        var service = new AnswerService(retriever, new FakeAnswerGenerator(), MIN_SCORE);
        assertFalse(service.answer("read-timeout 默认多少?").contains("文档中没有相关内容"));
    }

    @Test
    void maxActive默认_走回答路径() {
        var retriever = new FakeRetriever(Map.of(
                "max-active", List.of(new RetrievedChunk(
                        "max-active 默认 8", "配置说明.md", "连接池", 0.73))
        ));
        var service = new AnswerService(retriever, new FakeAnswerGenerator(), MIN_SCORE);
        assertFalse(service.answer("max-active 默认多少?").contains("文档中没有相关内容"));
    }

    // ===== 3 个「幻觉诱导」:fake 给低分块(< 0.3) =====

    @Test
    void 灰度发布_低分确定性拒答_且不调chat() {
        var retriever = new FakeRetriever(Map.of(
                "灰度", List.of(new RetrievedChunk(
                        "灰度相关描述", "配置说明.md", "开关配置", 0.18)) // 低分
        ));
        var fakeChat = new FakeAnswerGenerator();
        var service = new AnswerService(retriever, fakeChat, MIN_SCORE);

        String result = service.answer("服务支持灰度发布吗?");

        assertEquals("文档中没有相关内容", result, "低分应拒答");
        assertEquals(0, fakeChat.callCount(), "拒答路径绝不能调 chat(行为级保证,比断言文案更本质)");
    }

    @Test
    void maxActive上限_低分拒答_且不调chat() {
        var retriever = new FakeRetriever(Map.of(
                "max-active 上限", List.of(new RetrievedChunk(
                        "无上限描述", "配置说明.md", "连接池", 0.12))
        ));
        var fakeChat = new FakeAnswerGenerator();
        var service = new AnswerService(retriever, fakeChat, MIN_SCORE);
        assertEquals("文档中没有相关内容", service.answer("max-active 最大能配多少?"));
        assertEquals(0, fakeChat.callCount());
    }

    @Test
    void 不存在配置项_空结果也拒答_且不调chat() {
        // 关键词完全不匹配 → FakeRetriever 返回空 → 也应拒答
        var retriever = new FakeRetriever(Map.of());
        var fakeChat = new FakeAnswerGenerator();
        var service = new AnswerService(retriever, fakeChat, MIN_SCORE);
        assertEquals("文档中没有相关内容", service.answer("某个不存在的配置项是多少?"));
        assertEquals(0, fakeChat.callCount());
    }

}
