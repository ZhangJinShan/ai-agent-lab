package com.example.docqa.eval;

import com.example.docqa.qa.AnswerService;

import java.nio.file.Path;
import java.util.List;

/**
 * @author zhangjinshan
 * @description: 评估跑批:逐题 回答 → 判分 → 汇总。
 * @create: 2026/11/8
 **/
public class EvalRunner {

    private final AnswerService answerService;

    private final AnswerJudge answerJudge;

    private final DatasetLoader loader = new  DatasetLoader();

    public EvalRunner(AnswerService answerService, AnswerJudge answerJudge) {
        this.answerService = answerService;
        this.answerJudge = answerJudge;
    }

    public void run(Path datasetPath) throws Exception {
        List<EvalQuestion> questions = loader.load(datasetPath);

        int correct = 0;
        int judgeErrors = 0;

        StringBuilder archive = new StringBuilder(); // 逐题留档 → results-run1.md

        for (EvalQuestion question : questions) {
            String actual = answerService.answer(question.question());

            JudgeVerdict verdict = answerJudge.judge(question, actual);


        }


    }
}
