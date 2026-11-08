package com.example.docqa.eval;

/**
 * @author zhangjinshan
 * @description: judge 判分失败(输出解析不了 / chat 调用异常)。
 *  rawResponse 留档用于审计;EvalRunner catch 后计数 judge_error 并继续跑批。
 *  绝不返回默认分:混进指标里的错误判分比缺失的判分更危险。
 * @create: 2026/11/8
 **/
public class JudgeException extends RuntimeException {

    private final String rawResponse;

    public JudgeException(String message, String rawResponse, Throwable cause) {
        super(message, cause);
        this.rawResponse = rawResponse;
    }

    public String rawResponse() {
        return rawResponse;
    }

}
