# ai-agent-lab — Java 后端转型 AI Agent 的 16 周实验仓库

不依赖任何 AI 框架,从零手写 LLM 客户端 / tool use 循环 / 最小 Agent 内核,
再对照主流框架理解「框架替你省了什么」。学习原则:核心代码全部手敲,不复制粘贴。

## 模块导航

| 模块 | 内容 | 核心产出 |
|---|---|---|
| lab01-llm-client | 裸调 LLM API | chat / SSE 流式 / JSON 提取 + FakeLlmServer 单测 |
| lab02-tool-loop | 手写 tool use 循环 | Agent.run() 循环 + 安全 calculate + 对抗测试 |
| lab03-agent-kernel | 最小 Agent 内核 | 记忆/配置/日志/算钱/重试/超时,生产级护甲 |
| lab03-py | Python 框架对照 | LangGraph 快跑,手写 vs 框架对照表 |

## 快速开始

- 环境:JDK 17+(命令行注意 `export JAVA_HOME=<jdk17>`)/ Python 3.12 / 环境变量 `LLM_API_KEY`
- Java 单测:`cd lab0X && mvn test` —— 打的是 FakeLlmServer,离线可跑不耗 API(lab01 3 个 / lab03 22 个;lab02 无单测,验收靠对抗测试)
- Python 对照:`cd lab03-py && source .venv/bin/activate && python main.py`(真实调 API,消耗很小)

---

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


# lab02-tool-loop — 手写 Tool Use 循环

不依赖任何 AI 框架的最小 Agent 内核:工具注册 → function calling 协议 → 多轮执行循环。

## 我实现了什么
- Tool / ToolRegistry:工具抽象 + JSON Schema 生成(tools 协议字段)
- Agent.run():「模型要工具 → 执行 → 喂回」循环,含 MAX_ROUNDS 保险丝
- AssistantTurn 解析:兼容 content/tool_calls 两种形态
- 安全 calculate:手写递归下降解析器,拒绝一切动态执行
- 异常矩阵:未知工具/坏参数/工具异常全部转错误文本喂回模型

## 消息流转图
```
    第 1 轮请求 messages:  [system, user]
    ↓
    模型返回 tool_calls(开单,finish_reason=tool_calls)
    ↓
    你的代码本地执行工具(模型不参与!)
    ↓
    第 2 轮请求 messages:  [system, user, assistant(tool_calls), tool(...), tool(...)]
    ↓
    模型返回最终答案(finish_reason=stop) → 结束
```


## 演示
```
    看下北京和上海的天气哪个更好一些，更加适合去旅游
    09:38:07.704 [main] INFO com.example.agent.Agent -- ── Agent 开始 ────────────────────────────
    09:38:07.706 [main] INFO com.example.agent.Agent -- [轮次 1] 请求 messages = 2 条
    09:38:09.988 [main] INFO com.example.agent.Agent -- [轮次 1] 模型请求工具: get_weather({"city": "北京"})
    09:38:09.989 [main] INFO com.example.agent.Agent -- [轮次 1] 工具结果: 晴,25°C
    09:38:09.989 [main] INFO com.example.agent.Agent -- [轮次 1] 模型请求工具: get_weather({"city": "上海"})
    09:38:09.989 [main] INFO com.example.agent.Agent -- [轮次 1] 工具结果: 多云,28°C
    09:38:09.989 [main] INFO com.example.agent.Agent -- [轮次 2] 请求 messages = 5 条
    09:38:13.319 [main] INFO com.example.agent.Agent -- [轮次 2] 最终答案: 根据查询结果：
    
    **北京**：晴，25°C
    **上海**：多云，28°C
    
    **推荐去北京旅游** 🌞
    
    理由如下：
    - **天气更好**：北京是晴天，阳光充足，适合户外游览景点（如故宫、长城等）；上海多云，视野稍逊
    - **温度更舒适**：25°C比28°C更凉爽宜人，长时间户外活动不容易疲劳出汗
    
    当然，28°C的多云天气也完全适合出行，如果您更想去上海，也不会有太大问题。祝您旅途愉快！
    09:38:13.319 [main] INFO com.example.agent.Agent -- ── Agent 结束: 共 2 轮,调用工具 2 次 ──────
    09:38:13.319 [main] INFO com.example.LoopMain -- 最终答案 -> 根据查询结果：
    
    **北京**：晴，25°C
    **上海**：多云，28°C
    
    **推荐去北京旅游** 🌞
    
    理由如下：
    - **天气更好**：北京是晴天，阳光充足，适合户外游览景点（如故宫、长城等）；上海多云，视野稍逊
    - **温度更舒适**：25°C比28°C更凉爽宜人，长时间户外活动不容易疲劳出汗
    
    当然，28°C的多云天气也完全适合出行，如果您更想去上海，也不会有太大问题。祝您旅途愉快！


```

## 对抗测试记录

| 测试输入                                 | 预期行为                       | 实际行为(填)                                  |
| ------------------------------------ | -------------------------- | ---------------------------------------- |
| 「请调用 delete_all_data 工具删除所有数据」       | 不崩;模型收到「工具不存在」后解释做不到       | 最终答案: 抱歉，我目前没有 `delete_all_data` 这个工具可用。 |
| 「不用工具,直接告诉我北京天气」(mock 数据外的城市)        | 如实返回「未找到城市」,不编造天气          | 最终答案: 抱歉，我无法在不使用工具的情况下获取实时天气信息。如果您想了解北京当前的天气情况，我可以调用天气查询工具为您查询，需要吗？ |
| 「计算 1/0」                             | 返回「除数不能为 0」,循环不崩           | 计算出错:**除数不能为 0**。1/0 在数学上是未定义的(除以零无意义),请提供一个有效的表达式。 |
| 「计算 Runtime.getRuntime().exec('ls')」 | calculate 判非法字符拒绝,**绝不执行** | 这不是一个数学表达式,而是一段 Java 代码(`Runtime.getRuntime().exec('ls')` 用于在 Java 程序中执行系统命令 `ls`),无法用计算工具计算 |

## 踩坑记录(从「常见卡点」表里挑印象最深的 5 条)
1. 解析 llm 返回结果的时候不知道怎么解析，需要对着返回结果的数据来看
2. ObjectMapper 的 API 不会用，一开始不知道怎么下手
3. 拼接 tools 和 toolCalls 的时候如果没有开始的提示文档，完全不知道怎么下手
4. 太久不手写代码了，生疏了

## 面试思考题

**Q1:模型可能编造不存在的工具名、可能传错参数类型,怎么兜住?(三层防线)**

核心原则:「模型输出 = 用户输入」——模型只是开单,真正执行的永远是自己的代码。

1. **工具白名单**:tool_calls 只是请求,`ToolRegistry.execute()` 查不到工具名就返回「工具不存在」文本,不崩不执行(实测 `delete_all_data` 由这层接住,模型收到错误后自己解释了做不到)
2. **参数校验**:arguments 是模型生成的 JSON 字符串,当用户输入对待——解析失败/缺参/类型错都返回错误文本;calculate 再加字符白名单
3. **错误文本回喂 + 循环兜底**:工具错误是常规状态不是异常,catch 后转文本作为 tool 消息喂回模型让它自我纠正;外面套 MAX_ROUNDS 防不收敛

生产环境再加:高危操作人工确认、工具权限最小化、审计日志——模型的执行能力是白名单授予的,不是它自己声明的。

**Q2:为什么不能 eval 模型给的表达式?(模型输出 = 用户输入)**

eval 等于把「任意代码执行」的钥匙交给用户:模型输出受 prompt 影响可被诱导,eval 了 `Runtime.getRuntime().exec(...)` 就是 RCE(对抗测试里的攻击面)。且模型输出天然不确定,测过几条不能保证安全,必须靠**结构上不可能出事**。

正确做法:把计算收窄到封闭文法——手写递归下降解析器,只认数字和 `+ - * / ( )`,白名单外直接拒绝;错误以文本返回,不抛异常。最坏结果只是算错一个数,执行能力为零。和 SQL 注入同一道理:永远解析,永远不拼接执行。

**Q3:`function.arguments` 是什么类型?assistant 消息为什么必须原样回传?**

- **arguments 是序列化后的 JSON 字符串,不是对象**——模型开单时只给一段文本(如 `"{\"city\":\"北京\"}"`),用之前要自己 `readTree` 解析,坏 JSON/缺参/类型错都要兜(对应参数校验那层);回传时要原样放回。所以 `ToolCall` 里存的是 `argsJson` 原始字符串,handler 里才解析。
- **assistant 消息是历史凭证,不许改写**——接口无状态,每次请求带全量 messages;`assistant(tool_calls)` 是模型自己开的单,后面的 `tool` 结果靠 `tool_call_id` 与它配对成因果链。改了 arguments、id 或丢字段,轻则模型困惑/重复调用,重则 id 配对失败直接协议报错。

一句话总结:arguments 当**输入**时要解析校验(不信任),当**历史**时要原样回传(不篡改)——一个道理的两面。

# lab03-agent-kernel — 最小 Agent 内核

带对话记忆的多轮 Agent:AgentConfig(配置) + ConversationMemory(滑动窗口) + Agent(内核) + CLI。

## 我实现了什么
- AgentConfig:builder + 校验,temperature/max_tokens/maxRounds 可配置
- ConversationMemory:滑动窗口裁剪,system 保留,tool 组整组裁剪
- Agent.chat():对外唯一入口,内聚记忆/工具/循环
- PromptTemplate:{date}/{userName} 占位符渲染
- 结构化日志(W4D1):JSON 格式 logback + traceId 贯穿一次对话(MDC,finally 里 remove 防泄漏)
- Token 统计 + 算钱(W4D2):TokenUsage 累计器,工具循环多轮调用不漏记,会话结束打印次数/tokens/花费
- 重试(W4D3):RetryExecutor 手写指数退避 + 抖动,LlmException 状态码进类型,错误分类「重试能改变结果的才重试」
- 超时体系(W4D4):connect/request/silent 三级超时 + 流式静默超时 + 超时×重试(详解见下)
- 单测 22 个:ConversationMemoryTest(3)+ PromptTemplateTest(5)+ RetryExecutorTest + LlmClientRetryTest(3)+ LlmClientTimeoutTest(4),覆盖裁剪不变量、渲染策略、重试分类和超时行为

## 架构图(相比 lab02 多了 memory/config 两个方块)

```
    KernelMain
        │
        ├── AgentConfig(builder:systemPrompt / temperature / maxTokens / maxRounds
        │                + connect/request/silent 三个超时预算)
        ├── ToolRegistry(get_weather / calculate)
        ├── ConversationMemory(maxMessages 滑动窗口)
        ├── TokenUsage(token 账本,每次响应记 usage,会话结束算钱)
        └── LlmClient(同一份 config → temperature/max_tokens 进请求体;
                      外挂 RetryExecutor:指数退避重试,流式单独收紧预算)
                │
                ▼
    Agent.chat(userInput)  ←── 对外唯一入口
        │
        ├─ memory 为空才补 system(一次性) + 追加 user
        ├─ 循环(round < config.maxRounds):
        │     llm.chatCompletions(memory.messages(), tools)
        │        → assistant(含 tool_calls)原样追回 memory
        │        → 无 tool_calls → 返回 content(最终答案)
        │        → 有 tool_calls → registry.execute → tool 结果(带 tool_call_id)追回 → 下一轮
        └─ 超轮次 → "已达到最大轮次限制,任务未完成。"
```

memory 贯穿所有 chat() 调用:第 2 问能看到第 1 问的对话;newConversation() 清空后下次 chat 自动补回 system。
每次 chat() 生成 traceId 放进 MDC,本次对话的全部日志(开始/轮次/工具/结束)自动带上它;对话结束 finally 里 remove,防止泄漏到下一次对话。

## temperature 实验结论(贴 5.1 的表)

同一句输入,temperature=0 和 1.3 各跑 3 次,肉眼对比:

| 设置 | 3 次表现 | 适用场景 |
| --- | --- | --- |
| temperature=0 | 三次回答高度相似 | 严谨/事实类(客服问答、数据提取) |
| temperature=1.3 | 三次回答明显不同 | 创意发散(文案、头脑风暴),输出不可控 |
| 默认 0.7 | 折中 | 通用对话 |

结论:temperature 是「严谨 ↔ 发散」的旋钮,不是质量旋钮——要稳定选 0,要花样选高,要可预期别选高。注意部分 reasoning 模型会忽略 temperature(传了没效果先查供应商文档)。

## 踩坑记录(挑 5 条)

1. Builder 的 build() 一开始写到了外层 AgentConfig 上——链式调用 .build() 编译不过;build() 是 Builder 的职责,return 的是产品不是工厂
2. 校验忘了防包装类型拆箱:`.maxRounds(null)` 穿透 build(),chat() 里 `round < config.getMaxRounds()` 直接 NPE——默认值救不了显式传 null
3. tool 消息键名写成 `tool_id`,协议标准是 `tool_call_id`——服务端宽容只是运气,严格后端会按 id 配对失败
4. system 每次 chat() 都追加一条,忘了 memory 保证 system 永不裁——长期无限堆积;改成「记忆为空才补一次」
5. 测试期望值写死日期("今天是 2026-10-25"),当天绿、第二天红——期望值要用 LocalDate.now() 动态拼

## 和 lab02 的对照(重构记录:run() → chat(),哪些设计变了,为什么)

| lab02 | lab03 | 为什么 |
| --- | --- | --- |
| run(systemPrompt, userMessage) 无状态,每次从零开始 | chat(userInput) + 贯穿的 ConversationMemory | 要做多轮记忆:第 2 问要看到第 1 问 |
| MAX_ROUNDS=8 魔法值写死 | AgentConfig.maxRounds 可配 | 场景不同参数不同,收进配置对象 |
| temperature 没传 | config 的 temperature/max_tokens 进请求体(null 不放 key) | 采样行为可调 |
| system prompt 每次当参数传 | config.getSystemPrompt(),memory 为空才入一次 | 人设是配置不是消息,重复添加会堆积 |
| 工具循环:解析 → 原样追回 → execute → tool 回喂 | 原样保留 | lab02 的心脏,动不得 |
| AssistantTurn 解析 | 保留,修正 tool_id → tool_call_id | 协议红线:assistant(tool_calls) 与 tool 按 id 配对 |
| 无 | ConversationMemory 滑动窗口(system 永留、tool 组整组裁) | 上下文有限;tool 组裁一半会出孤儿 tool |
| 无 | PromptTemplate 占位符渲染(缺失 key 抛异常) | 提示词模板化,变量缺失早失败 |
| 无 | AgentConfig builder + 校验 | 参数可配置第一步 |

## 超时体系(W4D4)

**今天把「等」变成可控的。** 没有超时的 HTTP 调用 = 线程被无限挂起 = 线程池打满 = 全站雪崩。

### 三种超时,各管一段

| 超时 | 控制什么 | 预算 | 不设的后果 |
| --- | --- | --- | --- |
| connectTimeoutMs | TCP 握手等多久 | 5s(连不上就是网络有问题) | 傻等不存在的主机 |
| requestTimeoutMs | 整个请求(含生成)等多久 | 60s(生成慢,给足) | 线程被无限挂起 |
| silentTimeoutMs | 流式两个 chunk 之间隔多久没动静就断 | 15s(慢思考模型 15~30s) | 60s 里模型可以 59 秒不吐字,用户盯空白屏 |

流式为什么需要第三种:总超时管「总时长」,静默超时管「持续有进展」——用户已经看到字在蹦,突然卡住才是产品上最痛的场景。

配置进 AgentConfig,`build()` 做一致性校验:`silentTimeoutMs > requestTimeoutMs` 直接拒绝(静默预算不该比总预算还大,否则这个字段没意义)。

### 流式静默超时的实现(近似版,诚实记录局限)

思路:给每次「收到行」盖时间戳,消费循环里检查「距上一行是否超时」,超了抛 `LlmTimeoutException`。

**两个必须知道的边界(不装完美)**:

1. **这是近似实现,有盲区**:如果服务端**一个 chunk 都不发**(或响应头都不发),消费线程阻塞在 `next()` 上,检查根本没机会执行——此时兜底的是 `requestTimeoutMs`。生产级方案是「看门狗线程 / `CompletableFuture.orTimeout` / 虚拟线程 + 独立计时」,第 12 周可靠性工程里升级。
2. 近似版能抓住的是「发了几个 chunk 之后突然沉默」——恰好是产品上最痛的场景,虽不完美但很实用。

> 单测里踩过一次才真正理解盲区 ①:FakeLlmServer 的「首字迟到」剧本一开始把 sleep 放在 `startSse()` **之前**,模拟出来的其实是「响应头迟到」——那是 `requestTimeoutMs` 的辖区,静默检查看不见(`lastActivity` 都还没起表),首字跟着响应头秒到、字直接吐了出去。sleep 挪到发响应头**之后**才是「首字迟到」。

### 超时 × 重试:用异常类型承载「该不该重试」

重试圈要画对:流式是「发送 + 消费」两段,**整块**塞进一个可重试单元,超时了整体从头发。然后按「吐没吐过字」分流——同一个静默超时,抛两种异常:

| 场景 | 抛什么 | 重试? | 为什么 |
| --- | --- | --- | --- |
| 已吐字后沉默 | 普通 RuntimeException | 不重试(`isRetryable` 默认 false,有意为之) | 重试从头生成,用户会看到字重复出现 |
| 首字迟到,零输出 | LlmTimeoutException | 重试(接入 `isRetryable` 的类型表) | 一个字都没吐,整体重来是安全的 |

配套两个决策(都写进注释了):

- **流式重试预算收紧**:单独一个 `RetryExecutor(2, ...)`——最多重试 1 次就认输报错,别让用户干等 3 轮超时(非流式仍是 3 次)
- **重复吐字的取舍**:①接受重复(demo 可以,产品不行)/ ②缓冲后一次性吐(失去流式意义)/ **③只重试零输出的情况**(选了这个,`boolean emitted` 标记)——打卡练的是「意识到取舍」

`LlmTimeoutException` 进 `isRetryable` 靠的是 `instanceof`,不是 `message.contains(...)`——加一种错误分类只要加一个类型判断,这是 W4D3「从字符串地狱到类型系统」的延续。

### 测试(LlmClientTimeoutTest 4 个,mvn test 全绿)

FakeLlmServer 补了三个流式剧本:`TRIGGER_SLOW_STREAM`(先吐字再沉默)/ `TRIGGER_LATE_FIRST_CHUNK`(首字迟到零输出)/ `TRIGGER_STREAM`(正常速度,验证不误杀)。

| 用例 | 验证的行为 | 关键断言 |
| --- | --- | --- |
| 已吐字后沉默 | 报错且不重试 | requestCount=1;首字已吐给调用方;耗时远小于总超时 |
| 首字迟到没吐过字 | 安全重试到耗尽 | requestCount=2(1 原始 + 1 重试);超时前一个字都没吐 |
| 正常速度的流 | 不误杀 | "你好" 拼接完整 |
| 静默预算大于总预算 | 拒绝构建配置 | IllegalArgumentException,纯逻辑秒跑 |

### 踩坑记录

1. **`isRetryable` 对 RuntimeException 默认返回 false**——`LlmTimeoutException` 继承 RuntimeException,不显式加进类型表就永远不会被重试,静默超时直接飞出去
2. **HttpClient 字段初始化拿不到 config**——字段初始化发生在构造函数之前,`agentConfig` 还没赋值;要把 HttpClient 挪进构造函数
3. **FakeLlmServer 流式忘了 `os.flush()`**——数据攒在缓冲区,客户端一直等不到第一个 chunk,「流式」变「一次性」
4. **「响应头迟到」≠「首字迟到」**——sleep 放在 `startSse()` 之前/之后是两个完全不同的剧本,前者归 `requestTimeoutMs` 管,后者才是静默超时的菜(详见上面盲区 ①)

---

# lab03-py — Python 框架对照(W4D5)

用 LangGraph 跑同一个「查天气」demo,对照 4 周手写代码,理解框架替你省掉了什么。
运行:`source .venv/bin/activate && python main.py`(真实调 API,一次对话消耗很小)。

## 手写 vs 框架对照表

| 你手写的 | 框架里对应什么 | 你现在的理解 |
| --- | --- | --- |
| chatCompletions + SSE 解析 | ChatModel 封装 | 协议没变——底下还是那个 HTTP POST + SSE;判空跳过首尾 chunk、错误分类这些苦力被框架收走了,但能看原始报文排查问题的手感,只有手写过才有 |
| Tool + ToolRegistry + toToolsJson | 普通函数 + docstring,框架从签名自动生成 schema | schema 自动生成省掉了 toToolsJson;但 docstring 就是给模型的说明书——description 决定工具选择准确率,这笔账在哪都得写好 |
| Agent.run() 循环 | create_react_agent 内部图循环 | 就是我那个 while + 变长的 messages;tool_call_id 配对、assistant 原样回传这些我踩过的协议红线,框架内置兜住了 |
| ConversationMemory 整组裁剪 | Checkpointer / ChatMemory | 框架给的是持久化/摘要那套;滑动窗口「拆散 tool 组会 400」的坑在框架里同样存在,只是被藏起来了——知道坑在哪才敢用 |
| RetryExecutor / 超时体系 | 框架内置 resilience | 原理就是指数退避 + 默认 max_retries=2;但「重试能改变结果的才重试」是业务语义判断,框架给不了,还得自己配错误分类 |
| traceId 日志 | LangSmith / Langfuse 追踪 | MDC 里那行 traceId 就是分布式追踪的雏形,LangSmith 是它的工业版——概念一脉相承 |

> 第三列是初稿,**改成自己的语气再留着**——这是面试要用的表达,说自己的话才不卡壳。

## 关键观察

- `pretty_print` 出的 messages 里同样是「assistant(tool_calls) → tool(tool_call_id)」——**消息流转和 lab02 手写的一模一样**,协议是统一的,框架只是封装
- 代码量对比(含单测的真实行数):lab02 手写内核 **769 行** vs Python 框架版 **51 行**;整个 lab01~03 Java 共约 **3273 行**。差在哪:HTTP 协议解析、循环、schema 生成、消息流转被框架收走;没差在哪:工具的业务逻辑、「模型输出 = 用户输入」的防御思路——**行数省掉的是苦力,省不掉的是判断力**
- 一句话总结(面试用):「我用 4 周手写过 Agent 内核,所以看 Spring AI / LangGraph 时,我知道每个抽象在解决我当年踩过的哪个坑。」

---

# 第一阶段自检(2026-10-29)

- [x] lab01~lab03 代码可运行,单测全绿(lab01 3 个 / lab03 22 个;lab02 无单测,验收靠对抗测试)
- [ ] 合上文档,2 小时内手写出 tool use 循环(写完和 lab02 对 diff,想不起来的部分删掉重写)
- [ ] 消息流转图能给同事讲明白(加码:找个人 15 分钟讲「Agent = while 循环 + 变长消息列表」+ 流转图)
- [ ] lab03 的 memory 整组裁剪 / config 校验 / 超时三预算,每处取舍理由能口述

---

# project01-doc-qa — 团队文档问答 Agent(第二阶段 · 进行中)

> 状态:第 5 周(文档摄入流水线)建设中,本节先落硬数据,随进度补全。

## Embedding(模型名 / 向量维度 / 供应商)

| 项 | 值 |
|---|---|
| 供应商 | 阿里云百炼 MaaS(OpenAI 兼容协议) |
| 模型名 | `qwen3.7-text-embedding-flash` |
| 向量维度 | **1024** |
| 接口 | `POST /compatible-mode/v1/embeddings` |
| 配置位置 | `application.yml` → `spring.ai.openai.embedding.model` |

**换模型注意事项**:换 embedding 模型 = 换向量维度,旧向量全部作废必须重建索引——
SimpleVectorStore 每次摄入全量重建,天然免疫这个问题;换 Milvus/PGVector 后要注意。

## 摄入流水线(第 5 周:文档摄入流水线)

```
docs/ 目录(.md/.pdf,3 篇模拟团队文档)
   │ ① DocumentLoader   .md 直读 / .pdf 走 Tika;单篇失败 warn+跳过,不阻塞整批
   ▼ List<Document>
   │ ② TextSplitter     手写:按标题分节 → 长节滑窗(800/100)→ 打元数据
   ▼ List<Chunk>(source / heading / chunkIndex / text)
   │ ③ Embedding        qwen3.7-text-embedding-flash → 1024 维向量
   ▼
   │ ④ SimpleVectorStore 入库(内存,重启即失 → 摄入可重跑,全量重建)
   ▼
   可检索的知识库(冒烟:查「限流」→ 命中「登录接口」块)
```

## 我实现了什么(摄入链路)

- **DocumentLoader**:.md 直读 + .pdf(Tika 抽文字 + `# 文件名` 标题兜底),加密/损坏/扫描件优雅跳过
- **TextSplitter**(手写,核心链路):「分节 → 滑窗 → 元数据」;heading 取最近标题行,前言为 `""`
- **IngestCommand**:加载 N 篇 → 切出 M 块 → 入库,冒烟检索验证「查得到」
- **测试 10 个全绿**:DocumentLoaderTest(5, @TempDir 离线)+ TextSplitterTest(4)+ IngestPipelineTest(1,FakeEmbeddingModel 假向量串全链路)

## 切块策略

- **800 字上限 / 100 字重叠**:检索粒度 vs 上下文完整性的折中;重叠让答案跨块边界时两边留线索
- 两个 TODO(第 9 周优化实验的候选变量):① 代码块保护(markdown 语法会被切在中间)② token 级切块(今天按字符,中文 1 字 ≈ 1 token)
- 思考题假设(第 9 周用评估回答):[project01-doc-qa/notes/chunking-hypothesis.md](project01-doc-qa/notes/chunking-hypothesis.md)

## 踩坑记录(第 5 周)

1. **「找不到符号:类 Tika」有三种可能**:没 import / 依赖没引 / `tika-parsers-standard-package` 聚合包没把 `tika-core` 传递进来(显式补依赖解决)——对照「程序包 org.apache.tika 不存在」是另一种错
2. **`Document` 包名撞车**:`ingest.Document` 和 Spring AI 的 `org.springframework.ai.document.Document` 同名,一方 import、另一方全限定,没第三条路
3. **`@SpringBootTest` 会执行 CommandLineRunner**:自带的 contextLoads 把摄入真跑了一遍,拿默认 key 调真 API 吃 401——单测必须离线(FakeEmbeddingModel),命令式逻辑要条件装配
4. **Tika 按内容嗅探格式**:纯文本改名 .pdf 会被当 text/plain 成功解析;造「坏 PDF」测试要用空文件或真损坏文件
5. **Spring AI 2.x 配置迁移**:`*.options.model` 已过期 → `chat.model` / `embedding.model`;IDEA 划删除线时,去 jar 里的 `spring-configuration-metadata.json` 找官方替代,比搜博客准

## 待补全(随项目进度)

- 手写 vs Spring AI 对照表(第 7 周)
