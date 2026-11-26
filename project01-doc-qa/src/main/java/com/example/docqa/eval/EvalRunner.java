package com.example.docqa.eval;

import com.example.docqa.exception.JudgeException;
import com.example.docqa.qa.AnswerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhangjinshan
 * @description: 评估跑批:逐题 回答 → 判分 → 汇总。
 * @create: 2026/11/8
 **/
public class EvalRunner {

    private static final Logger log  = LoggerFactory.getLogger(EvalRunner.class);

    private static final int CELL_MAX = 200;

    private final AnswerService answerService;

    private final AnswerJudge judge;

    private final DatasetLoader loader;

    public EvalRunner(AnswerService answerService, AnswerJudge judge) {
        this.answerService = answerService;
        this.judge = judge;
        loader = new DatasetLoader();
    }


    public RunSummary run(Path datasetPath) throws IOException {
        return run(datasetPath, Path.of("eval/results-run1.md"));
    }

    public RunSummary run(Path datasetPath, Path archivePath) throws IOException{
        List<EvalQuestion> questions = loader.load(datasetPath);

        int correct = 0;
        int judgeErrors = 0;
        int answerErrors = 0;

        StringBuilder archive = new StringBuilder(); // 逐题留档 → results-run1.md

        archive.append("# 评估跑批留档\n\n")
                .append("| id | type | score | reason | question | actual | 备注 |\n")
                .append("|---|---|---|---|---|---|---|\n");
        // 收集 Metrics.ItemResult
        List<Metrics.ItemResult> itemResultList = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            EvalQuestion q = questions.get(i);
            log.info("[{}/{}] id={} type={}", i + 1, questions.size(), q.id(), q.type());
            String actual = "";
            try {
                String answer = answerService.answer(q.question());
                actual = answer == null ? "" : answer;
            } catch (Exception e) {
                // 单体失败不中断整批: 50 提跑十几分钟，中止等于白跑
                answerErrors ++;
                log.warn("answer 失败, id = {}", q.id(), e);
                archive.append(row(q, "ERROR", "", "", "answer_error: " + e.getMessage()));
            }
            JudgeVerdict verdict;
            try {
                verdict = judge.judge(q, actual);
            } catch (JudgeException e){
              judgeErrors ++;
              log.warn("judge 判分失败，id = {}, raw = {}", q.id(), e.rawResponse(), e);
              archive.append(row(q, "ERROR", "", actual, "judge_error: " + e.getMessage() + " raw=" + e.rawResponse()));
              itemResultList.add(new Metrics.ItemResult(q.id(), q.type(), -1, true, CitationChecker.isAccurate(actual, q.cite()), hasCite(q), Metrics.isAbstainAnswer(actual)));
              continue;
            } catch (RuntimeException e) {
                judgeErrors ++;
                log.warn("judge 异常, id = {}", q.id(), e);
                archive.append(row(q, "ERROR", "", actual, "judge_error: " + e.getMessage()));
                itemResultList.add(new Metrics.ItemResult(q.id(), q.type(), -1, true, CitationChecker.isAccurate(actual, q.cite()), hasCite(q), Metrics.isAbstainAnswer(actual)));
                continue;
            }
            if (verdict.score() == 1) {
                correct ++;
            }
            archive.append(row(q, String.valueOf(verdict.score()), verdict.reason(), actual, verdict.rawResponse()));

            itemResultList.add(new Metrics.ItemResult(q.id(), q.type(), verdict.score(), false, CitationChecker.isAccurate(actual,  q.cite()), hasCite(q), Metrics.isAbstainAnswer(actual)));
        }

        Metrics.Report report = Metrics.summarize(itemResultList);
        RunSummary summary = new RunSummary(questions.size(), correct, judgeErrors, answerErrors);
        archive.append("\n## 汇总\n\n")
                .append("- 总题数: ").append(summary.total()).append('\n')
                .append("- 正确: ").append(summary.correct()).append('\n')
                .append("- judge_error: ").append(report.judgeErrors()).append('\n')
                .append("- answer_error: ").append(summary.answerErrors()).append('\n')
                .append("- 回答正确率: ").append(pct(report.answerAccuracy())).append('\n')
                .append("- 引用准确率: ").append(pct(report.citationAccuracy())).append('\n')
                .append("- 拒答判断准确率: ").append(pct(report.abstainAccuracy())).append('\n')
                .append("  - abstain 拒答召回率: ").append(pct(report.abstainRecall())).append('\n')
                .append("  - 误拒率: ").append(pct(report.falseAbstainRate())).append('\n');
        Path parent = archivePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(archivePath, archive.toString(), StandardCharsets.UTF_8);
        log.info("跑批完成: total={} correct={} judge_error={} answer_error={} "
                        + "answerAccuracy={} citationAccuracy={} abstainAccuracy={} abstainRecall={} falseAbstainRate={}",
                summary.total(), summary.correct(), report.judgeErrors(), summary.answerErrors(),
                pct(report.answerAccuracy()), pct(report.citationAccuracy()),
                pct(report.abstainAccuracy()), pct(report.abstainRecall()),
                pct(report.falseAbstainRate()));
        return summary;
    }

    private static String row(EvalQuestion q, String score, String reason, String actual, String note) {
        return "| " + q.id()
                + " | " + cell(q.type())
                + " | " + cell(score)
                + " | " + cell(reason)
                + " | " + cell(q.question())
                + " | " + cell(actual)
                + " | " + cell(note)
                + " |\n";
    }

    /** Markdown 单元格:actual/reason 里的换行与竖线会冲烂表格,截断便于人工抽查 */
    private static String cell(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replace("\r", " ").replace("\n", " ").replace("|", "\\|");
        return cleaned.length() > CELL_MAX ? cleaned.substring(0, CELL_MAX) + "…" : cleaned;
    }

    /** 率字段为 null 时输出 N/A；其余按百分比打印 */
    private static String pct(Double value) {
        return value == null ? "N/A" : String.format("%.2f%%", value * 100);
    }

    /**
     * 判断 question 是否存在引用
     * @param question
     * @return
     */
    private boolean hasCite(EvalQuestion question){
        return question.cite() != null && !question.cite().isBlank();
    }
}
