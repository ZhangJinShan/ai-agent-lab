package com.example.docqa.eval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author zhangjinshan
 * @description: 引用准确率判定(规则型, 不用 LLM)。
 * @create: 2026/11/24
 **/
public class CitationChecker {

    private static final Logger logger = LoggerFactory.getLogger(CitationChecker.class);

    /** 回答模板约定的引用格式：[来源:文件#标题] —— 抽取用 **/
    private static final Pattern CITATION = Pattern.compile("\\[来源:([^#\\]]+)#([^\\]]*)\\]");

    public record Citation(String source, String heading){}

    /**
     * 抽取回答里所有引用标注
     * @param answer
     * @return
     */
    public static List<Citation> extract(String answer){
        List<Citation> citations = new ArrayList<>();
        Matcher matcher = CITATION.matcher(answer);
        while (matcher.find()){
               citations.add(new Citation(matcher.group(1).trim(), matcher.group(2).trim()));
        }
        return citations;
    }

    /**
     * 引用准确:回答的引用里命中了期望出处。
     * 松紧度(这是「引用准确率」的定义,记进 README):
     *      source 必须精确相等;
     *      heading 宽松匹配(contains) —— 模型可能写 "#登录接口(注意事项)",严格相等会冤枉它。
     * @param answer
     * @param expectedCite
     * @return
     */
    public static boolean isAccurate(String answer, String expectedCite){
        // 1. expectedCite 判空
        if (expectedCite == null || expectedCite.isBlank()) {
            return true;
        }
        // 2. expectedCite 拆成 source + heading, 示例: 接口文档.md#登录接口
        String[] split = expectedCite.split("#", 2);
        if (split.length < 2) {
            return false;
        }
        String source = split[0].trim();
        String heading = split[1].trim();
        // 3. 遍历 extract(answer):source 相等 && (heading空 || 实际 heading contains 期望 heading)→ true
        List<Citation> citationList = extract(answer);
        for (Citation citation : citationList) {
            if (source.equals(citation.source) && (heading == null || heading.isEmpty() || citation.heading.contains(heading))) {
                return true;
            }
        }
        // 4. 都没命中 → false
        return false;
    }
}
