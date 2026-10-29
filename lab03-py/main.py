"""
lab03-py — Python 对照实验(只求跑通,感受差异)

对照 lab02 手写的 Java 版,体会框架替你做了什么:
- get_weather 函数 + docstring  ≈ 你的 Tool record + JSON Schema(框架从签名自动生成)
- create_react_agent 内部的循环 ≈ 你的 Agent.run()(模型要工具 → 执行 → 喂回)
- result["messages"]           ≈ 你的 messages 列表(assistant(tool_calls) → tool 消息全在里面)

跑之前:
    export LLM_API_KEY=sk-你的key        # 同 Java 侧
    export LLM_BASE_URL=...             # 可选,默认阿里云 MaaS 兼容端点
    python main.py
"""
import os

from langchain_openai import ChatOpenAI
from langgraph.prebuilt import create_react_agent

llm = ChatOpenAI(
    model_name="glm-5.3",
    openai_api_key=os.environ["LLM_API_KEY"],
    openai_api_base=os.environ.get(
        "LLM_BASE_URL",
        "https://llm-t434y2d2k03iub6z.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
    ),
)


def get_weather(city: str) -> str:
    """查询指定城市的当前天气

    Args:
        city: 城市名,如:北京
    """
    # 同 lab02 的 mock 数据,今天不考天气准不准
    weather = {"北京": "晴,25°C", "上海": "多云,28°C", "广州": "雷阵雨,30°C"}
    return weather.get(city, f"未找到城市: {city}")


# 普通函数直接传入,框架自动从签名+docstring 生成工具 schema(对比你手写的 toToolsJson)
agent = create_react_agent(
    llm,
    tools=[get_weather],
    prompt="你是一个能查天气的助手,回答要简洁",  # 即 system prompt
)

result = agent.invoke({"messages": [("user", "北京今天天气怎么样?")]})

# 重点观察:下面每条消息的角色/字段,和你 W2 画的消息流转图一一对比
for m in result["messages"]:
    m.pretty_print()
