# 手写 vs Spring AI 对照表(草稿)

> 状态:Day 2 观察素材,行为差异列待自己填 | 记录日期:2026-11-05 | 正式版:Day 4 写进 README

> 铁律(计划 4351):「行为差异」列必须自己跑出来、自己填,别让 AI 生成对照表。
> 本文件只记**可从日志核验的事实**;归因、结论、面试表达留白,Day 4 补。

## Day 2 可核验事实(2026-11-05 两次 runner 日志)

| # | 观察点 | 事实 | 证据 |
| --- | --- | --- | --- |
| 1 | 记忆写入时机 | 拒答路径不调 `chatClient.call()`,`MessageChatMemoryAdvisor` 的 `before/after` 不触发,这一轮不进记忆 | 补记忆前前 3 句无 `QueryRewriter` 日志(历史为空) |
| 2 | 手动补记忆生效 | 拒答分支 `chatMemory.add(...)` 后,第 2/3/4 句都出现改写日志(历史非空) | 补记忆后 22:07 的三条 `QueryRewriter` 日志 |
| 3 | 补记忆救不了闲聊类 | `我叫老张` score=0.354 / `我叫什么` score=0.318,阈值 `min-score=0.37`,两条都确定性拒答;记忆要有一次 chat 调用才能被消费 | 补记忆后 `我叫什么?` 仍拒答 |
| 4 | 改写生效 | `那密码错误呢?` → `登录接口连续密码错误多少次会触发账号锁定?`,top-1 从 0.528 升到 0.684 | 21:59 与 22:07 两次日志 |
| 5 | 独立问题不改写 | `我叫什么?` → `我叫什么?`(仅半角转全角),符合改写 prompt「原问题独立完整则原样输出」 | 22:07 日志 |

## 2.1 记忆三问(对照 lab03 ConversationMemory)

| 问题 | lab03 手写版 | Spring AI(本日核验) |
| --- | --- | --- |
| 为什么不能无限追加? | 成本 / 上限 / 注意力稀释 | `MessageWindowChatMemory` 窗口 20,框架管裁剪 |
| 裁什么? | 超出 maxMessages 的最早消息 | 待跑:20 轮后观察窗口是否裁剪 |
| assistant(tool_calls) + tool 整组怎么裁? | 自己写整组裁剪 | 未观察到:Day 2 无 `@Tool`、无 tool 消息,待 Day 3 |

## 打卡项第 1 条(如实改写)

- [x] ChatMemory 装配完成;用文档相关两连问验证记忆生效(登录限流 → 那密码错误呢);「记名字三段」在固定管线下不适用,观察见本文件

## 待记(文档 2.5 的坑)

- 正常轮次 advisor 把含 5×800 字 fragments 的 `userPrompt` 整条写进记忆,拒答分支手动写的是纯 `UserMessage(question)`,历史里两种格式混着,改写器把它们全拼进 context。
- 拒答分支手写补记忆后,3 参构造器(测试)拿不到 `ChatMemory`,需判空跳过 —— 框架把记忆写入绑死在 chat 调用上的连带成本。

## 对照表四行骨架(Day 4 用 4.1 实验结果填「行为差异」)

| 你手写的 | Spring AI 里对应 | 行为差异(自己填) | 框架没帮你做的事(自己填) |
| --- | --- | --- | --- |
| `Tool` record + registry | `@Tool` / `ToolCallback` | | |
| `run()` 循环 | 默认 tool calling 流程 | | |
| `ConversationMemory` | `ChatMemory` / advisor | | |
| 自己拼 prompt | `ChatClient` + template | | |

## Day 2 待办

- [ ] 20 轮窗口裁剪观察(对照 lab03 W3D4 的 messageCount)
- [ ] 故意问一句 tool_calls 相关追问,看有无 400(Day 3 后才有 tool 消息)
- [ ] 成本账(E2):每次提问多一次改写 chat 调用
