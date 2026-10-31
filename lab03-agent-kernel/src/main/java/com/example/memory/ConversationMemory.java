package com.example.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author zhangjinshan
 * @description: 对话历史 + 滑动窗口裁剪。
 *  system 消息永远保留(它是人设,丢了就失忆了),其余超过 maxMessages 条时丢最早的。
 * @create: 2026/10/24
 **/
public class ConversationMemory {

    private final int maxMessages;          // 窗口大小,如 20

    private final List<Map<String, Object>> messages = new ArrayList<>();

    public ConversationMemory(int maxMessages) {
        if (maxMessages < 2) {
            throw new IllegalArgumentException("maxMessages 至少装得下一轮问答");
        }
        this.maxMessages = maxMessages;
    }

    /** 追加一条消息,追加后触发裁剪 */
    public void add(Map<String, Object> message) {
        // 追加消息
        messages.add(message);
        // 裁剪
        trim();
    }

    /** 返回当前全部消息(给 LlmClient 发请求用) */
    public List<Map<String, Object>> messages() {
        // 防御式拷贝,别把内部 list 暴露出去让人随便改
        return List.copyOf(messages);
    }

    /** 清空历史(CLI 的 /new 命令用) */
    public void clear() { messages.clear(); }

    private void trim() {
        // 非 system 消息没有达到上限，不用裁剪
        if (nonSystemCount() <= maxMessages) {
            return;
        }

        while(nonSystemCount() > maxMessages) {
            int start = firstNonSystemIndex(); // 找出第一个非system消息的下标
            if (start == -1) { // 表示没有找到，全是 system 消息
                break; // 终止循环直接返回
            }
            int end = groupEnd(start);
            // 如果不存在，也不要裁剪了，否则就只剩下 system 消息了，没有上下文了
            if (!hasNonSystemAfter(end)) {
                break;
            }
            messages.subList(start, end).clear();
        }
    }

    private boolean isSystem(Map<String, Object> m) {
        return "system".equals(m.get("role"));
    }

    private boolean hasToolCalls(Map<String, Object> m) {
        return m.get("tool_calls") != null;   // assistant 消息里有 tool_calls 字段就是工具组头
    }

    /**
     * 统计非系统消息数
     * @return
     */
    private long nonSystemCount(){
        return messages.stream().filter(message -> !isSystem(message)).count();
    }

    /**
     * 找到第一个不是 system 消息的下标值
     * 如果返回值是 -1 表示没有找到，即不存在 非system 的消息
     * @return
     */
    private int firstNonSystemIndex(){
        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> message = messages.get(i);
            if (!isSystem(message)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 从 start 开始,算出这一组的结束下标(不含)
     * 普通纸条:start+1
     * 组头(assistant 带 tool_calls):start+1,再把后面连续的 tool 纸条都吃进来
     * @param start
     * @return
     */
    private int groupEnd(int start){
        int end = start + 1;
        if (hasToolCalls(messages.get(start))) {
            while (end < messages.size() && "tool".equals(messages.get(end).get("role"))) {
                end ++;
            }
        }
        return end;
    }

    /**
     * 判断某个索引下标之后是否存在非 system 消息
     * @param index
     * @return
     */
    private boolean hasNonSystemAfter(int index){
        for (int i = index; i < messages.size(); i++) {
            Map<String, Object> message = messages.get(i);
            if (!isSystem(message)) {
                return true;
            }
        }
        return false;
    }
}
