package com.example.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * @author zhangjinshan
 * @description: 工具实现
 * @create: 2026/10/25
 **/
public class ToolFunction {

    private static ObjectMapper mapper = new ObjectMapper();

    /**
     * 根据参数中的 city 获取对应的天气信息
     * @param argJson
     * @return
     */
    public static String getWeather(String argJson){
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
    public static String calculate(String argJson){
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
