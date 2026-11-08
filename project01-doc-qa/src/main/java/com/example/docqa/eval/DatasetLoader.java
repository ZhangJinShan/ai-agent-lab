package com.example.docqa.eval;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author zhangjinshan
 * @description:
 *  dataset.jsonl 加载:一行一题。
 *  评估集是评估的根 —— 坏数据必须早炸,宁可炸在加载,不炸在跑批中途(炸在中途=白跑半小时)。
 * @create: 2026/11/8
 **/
public class DatasetLoader {

    private static final Set<String> LEGAL_TYPES = Set.of(
            "factual", "abstain", "trap"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<EvalQuestion> load(Path jsonl) throws IOException {

        if (!Files.isRegularFile(jsonl)) {
            throw new IllegalArgumentException("数据集不存在: " + jsonl.toAbsolutePath());
        }
        List<EvalQuestion> questions = new ArrayList<>();
        Set<Integer> seenId = new HashSet<>();
        List<String> lines = Files.readAllLines(jsonl); // UTF-8 默认
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isBlank()) {
                continue; // 空行跳过
            }
            EvalQuestion question;

            try {
                question = objectMapper.readValue(line, EvalQuestion.class);
            }  catch (IOException e) {
                // 坏行 → 报错带行号（不静默提跳过！评估集里藏坏题 = 指标悄悄失真）
                throw new IllegalArgumentException("数据集第" + (i + 1) + "行解析失败: " + e.getMessage(), e);
            }
            if (!seenId.add(question.id())) {
                throw new IllegalArgumentException("数据集 id 重复: " +  question.id());
            }
            if (!LEGAL_TYPES.contains(question.type())) {
                throw new IllegalArgumentException("第 " + question.id() + " 题 type 非法: " + question.type());
            }
            questions.add(question);
        }

        return questions;
    }
}
