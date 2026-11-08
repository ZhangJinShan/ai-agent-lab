package com.example.docqa.runner;

import com.example.docqa.eval.AnswerJudge;
import com.example.docqa.eval.DatasetLoader;
import com.example.docqa.eval.EvalQuestion;
import com.example.docqa.eval.JudgeVerdict;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * @author zhangjinshan
 * @description: Eval Command Line Runner
 * @create: 2026/11/8
 **/
@ConditionalOnProperty(name = "doc-qa.runner.eval", havingValue = "true")
@Component
public class EvalCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalCommandLineRunner.class);

    private final ChatClient agenticClient;

    public EvalCommandLineRunner(ChatClient agenticClient) {
        this.agenticClient = agenticClient;
    }

    @Override
    public void run(@NotNull String... args) throws Exception {
        DatasetLoader datasetLoader = new DatasetLoader();
        List<EvalQuestion> evalQuestionList = datasetLoader.load(Path.of("eval/dataset.jsonl"));
        AnswerJudge answerJudge = new AnswerJudge(agenticClient);
        EvalQuestion question = evalQuestionList.get(0);
        JudgeVerdict verdict = answerJudge.judge(question, "应引用 接口文档.md#登录接口,提到单 IP 100 次/分钟、超过返回 429");
    }
}
