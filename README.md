# lab01-llm-client — 裸调 LLM API 练手

一个不依赖任何 AI 框架的 Java LLM 客户端:同步调用 / SSE 流式 / JSON 结构化提取。

## 快速开始
- 环境要求:JDK 17+,环境变量 LLM_API_KEY
- 配置:application.properties(baseUrl/model)
- 运行:Main 的三种模式(普通对话 / 流式 / 提取)

## 我实现了什么
- chat():OpenAI 兼容协议同步调用,含错误处理(状态码表)
- chatStream():SSE 解析,判空跳过首尾 chunk
- extractJson():JSON 提取 + 失败重试 + 截断检查

## 测试
- FakeLlmServer(JDK HttpServer 手写桩服务)驱动 3 个单测,mvn test 全绿

## 踩坑记录(从「常见卡点」表里挑印象最深的 5 条)
1. AI 写代码习惯了，看着要求，我都不知道怎么写代码了，这个不行，得找补回来，哪怕是抄代码，也得慢慢写回来

## LLM 不确定性观察(Day 4 对抗测试的发现)
- 喂诗时的表现: 其实两种模式相对来说还是比较准确的，我觉得现在的模型比较强大了