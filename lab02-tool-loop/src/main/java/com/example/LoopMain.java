package com.example;

import com.example.agent.Agent;
import com.example.agent.Tool;
import com.example.agent.ToolRegistry;
import com.example.llm.LlmClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.function.Function;

public class LoopMain {

    private static final Logger logger = LoggerFactory.getLogger(LoopMain.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {

        // 初始化Tool注册器
        ToolRegistry registry = initToolRegistry();

        // 初始化 llm
        LlmClient llmClient = initLlmClient();
        if (null == llmClient) {
            logger.error("llmClient init fail");
            return;
        }

        // 初始化 Agent
        Agent agent = new Agent(
                llmClient,
                registry
        );

        // 调用 Agent
        Scanner scanner = new Scanner(System.in);
        String userMessage = scanner.nextLine();
        String answer = agent.run("你是一个智能助手,回答要简洁准确，如果不知道就直接回答不知道，不要捏造答案", userMessage);
        logger.info("最终答案 -> {}", answer);
    }

    /**
     * 初始化工具注册器
     * @return
     */
    private static ToolRegistry initToolRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(
                new Tool(
                        "get_weather",
                        "查询指定城市的当前天气",
                        "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"城市名\"}},\"required\":[\"city\"]}",
                        LoopMain::getWeather)
                );
        registry.register(
                new Tool(
                        "calculate",
                        "计算用户提供的表达式的结果并返回",
                        "{\"type\":\"object\",\"properties\":{\"expression\":{\"type\":\"string\",\"description\":\"表达式\"}},\"required\":[\"expression\"]}",
                        LoopMain::calculate)
        );
        return registry;
    }


    /**
     * 初始化 llm
     * @return
     */
    private static LlmClient initLlmClient() {

        // 1. 对 classpath 下的 application.properties 文件
        Properties properties = new Properties();
        try (InputStream in = LoopMain.class.getClassLoader().getResourceAsStream("application.properties")){
            if (in == null) {
                System.err.println("找不到 application.properties,检查文件是否在 src/main/resources 下");
                return null;
            }
            properties.load(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 2. 读环境变量里的 api-key
        String apiKey = System.getenv("LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("请先设置环境变量 LLM_API_KEY(设置后需重启 IDE/终端)");
            return null;
        }

        // 3. 构造 client —— 参数不对会抛 IllegalArgumentException
        return new LlmClient(properties.getProperty("llm.baseUrl"), properties.getProperty("llm.model"), apiKey);
    }

    /**
     * 根据参数中的 city 获取对应的天气信息
     * @param argJson
     * @return
     */
    private static String getWeather(String argJson){
        Map<String, String> weatherMap = Map.of(
                "北京", "晴,25°C",
                "上海", "多云,28°C",
                "广州", "雷阵雨,30°C"
        );
        try {
            JsonNode jsonNode = mapper.readTree(argJson);
            String city = jsonNode.path("city").asText();

            return weatherMap.containsKey(city) ? weatherMap.get(city) : "未找到城市 -> " + city;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据参数中的 expression 计算得到一个结果并返回
     * @param argJson
     * @return
     */
    private static String calculate(String argJson){
        try {
            JsonNode jsonNode = mapper.readTree(argJson);
            String expression = jsonNode.path("expression").asText();
            return doCalculate(expression);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 计算表达式(支持 + - * / 与括号):
     *   1. 校验:只允许数字、+ - * / ( ) 和空格
     *   2. 非法字符(字母/分号等)→ 直接返回 "非法表达式: 含不允许的字符"
     *   3. 解析计算,返回结果字符串,如 "结果: 14"
     *   4. 除零 → 返回 "错误: 除数不能为 0"(错误文本,不是异常)
     */
    private static String doCalculate(String s) {
        // 1. 字符白名单校验
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= '0' && c <= '9') && "+-*/()".indexOf(c) < 0 && c != ' ') {
                return "非法表达式: 含不允许的字符";
            }
        }

        try {
            // 3. 解析计算
            double result = new ExpressionParser(s).parse();
            return "结果: " + formatResult(result);
        } catch (ArithmeticException e) {
            // 4. 除零:返回错误文本,不向上抛异常
            return "错误: 除数不能为 0";
        } catch (RuntimeException e) {
            // 括号不匹配、缺少运算数等格式问题
            return "非法表达式: 格式错误";
        }
    }

    /** 结果是整数时去掉小数部分,如 14.0 → "14",3.5 → "3.5" */
    private static String formatResult(double result) {
        return result == Math.floor(result)
                ? String.valueOf((long) result)
                : String.valueOf(result);
    }

    /**
     * 递归下降解析器(标准四则运算文法):
     *   expr   := term (('+' | '-') term)*
     *   term   := factor (('*' | '/') factor)*
     *   factor := ('+' | '-') factor | number | '(' expr ')'
     */
    private static class ExpressionParser {
        private final String s;
        private int pos;

        ExpressionParser(String s) {
            this.s = s;
        }

        double parse() {
            double value = parseExpr();
            skipSpaces();
            if (pos < s.length()) {
                throw new RuntimeException("表达式末尾有多余字符");
            }
            return value;
        }

        private double parseExpr() {
            double value = parseTerm();
            while (true) {
                skipSpaces();
                char c = peek();
                if (c == '+' || c == '-') {
                    pos++;
                    double right = parseTerm();
                    value = c == '+' ? value + right : value - right;
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipSpaces();
                char c = peek();
                if (c == '*' || c == '/') {
                    pos++;
                    double right = parseFactor();
                    if (c == '*') {
                        value *= right;
                    } else {
                        if (right == 0) {
                            throw new ArithmeticException("除数不能为 0");
                        }
                        value /= right;
                    }
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            skipSpaces();
            char c = peek();
            if (c == '+') {          // 一元正号
                pos++;
                return parseFactor();
            }
            if (c == '-') {          // 一元负号
                pos++;
                return -parseFactor();
            }
            if (c == '(') {
                pos++;
                double value = parseExpr();
                skipSpaces();
                if (peek() != ')') {
                    throw new RuntimeException("缺少 )");
                }
                pos++;
                return value;
            }
            return parseNumber();
        }

        private double parseNumber() {
            skipSpaces();
            int start = pos;
            while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') {
                pos++;
            }
            if (pos == start) {
                throw new RuntimeException("缺少数字");
            }
            return Double.parseDouble(s.substring(start, pos));
        }

        private char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        private void skipSpaces() {
            while (pos < s.length() && s.charAt(pos) == ' ') {
                pos++;
            }
        }
    }
}
