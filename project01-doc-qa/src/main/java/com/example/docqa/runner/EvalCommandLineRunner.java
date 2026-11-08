package com.example.docqa.runner;

import com.example.docqa.eval.DatasetLoader;
import com.example.docqa.eval.EvalQuestion;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author zhangjinshan
 * @description: Eval Command Line Runner
 * @create: 2026/11/8
 **/
@ConditionalOnProperty(name = "doc-qa.runner.eval", havingValue = "true")
@Component
public class EvalCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalCommandLineRunner.class);

    @Override
    public void run(@NotNull String... args) throws Exception {
        DatasetLoader datasetLoader = new DatasetLoader();
        List<EvalQuestion> evalQuestionList = datasetLoader.load(Path.of("eval/dataset.jsonl"));
        Map<String, List<EvalQuestion>> listMap = evalQuestionList.stream().collect(Collectors.groupingBy(EvalQuestion::type, Collectors.toList()));
        for (Map.Entry<String, List<EvalQuestion>> entry : listMap.entrySet()) {
            log.info("Evaluating questions for {}, size = {}", entry.getKey(),  entry.getValue().size());
        }
    }
}
