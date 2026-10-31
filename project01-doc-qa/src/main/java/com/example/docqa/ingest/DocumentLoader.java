package com.example.docqa.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.tika.Tika;

/**
 * @author zhangjinshan
 * @description: 文档加载器:递归遍历目录,按扩展名分派读取(.md 直读 / .pdf 走 Tika)。
 * 只管「文件 → Document」,不切块不向量(流水线第一环)。
 * 原则:单篇失败不阻塞整批 —— 坏文档 warn + 跳过,好文档照常入库。
 * @create: 2026/10/31
 **/
public class DocumentLoader {

    private static final String MD_SUFFIX = ".md";

    private static final String PDF_SUFFIX = ".pdf";

    private static final Logger log = LoggerFactory.getLogger(DocumentLoader.class);

    private final Path docsDir;

    /**
     * Tika 门面:格式探测 + 解析的统一入口(底下帮你调 pdfbox)。
     * 以后加 Word/Excel 只换文件不用换代码 —— 这是选 Tika 不选 pdfbox 的原因。
     */
    private final Tika tika = new Tika();

    public DocumentLoader(String docDirStr) {
        if (null == docDirStr || docDirStr.isEmpty()) {
            throw new IllegalArgumentException("docDirStr 不能为空");
        }
        Path dir = Paths.get(docDirStr);
        if (!Files.isDirectory(dir)) {
            // 防御点 1:目录不存在/不是目录,构造时就炸,带上路径方便排查
            throw new IllegalArgumentException("文档目录不存在或不是目录: " + dir.toAbsolutePath());
        }
        this.docsDir = dir;
    }

    /** 递归读取目录下所有 .md 文件, 按路径排序返回 */
    public List<Document> loadAll() throws IOException {
        try (Stream<Path> paths = Files.walk(docsDir)) {
            return paths
                    .filter(Files :: isRegularFile) // 只要文件，不要目录
                    .filter(this::isSupported) // 支持 .md / .pdf 文件
                    .filter(path -> !path.getFileName().toString().startsWith(".")) // 过虑调隐藏文件
                    .sorted() // 决策: 排序保确定性
                    .map(this::loadOne)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    /**
     * 支持的扩展名:.md 和 .pdf,其余静默跳过
     */
    private boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(MD_SUFFIX) || name.endsWith(PDF_SUFFIX);
    }

    /**
     * 按扩展名分派读取;读不了返回 null(loadAll 会过滤掉)
     */
    private Document loadOne(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(MD_SUFFIX)) {
            return loadMd(path);
        }
        if (name.endsWith(PDF_SUFFIX)) {
            return loadPdf(path);
        }
        return null;
    }

    private Document loadMd(Path path) {
        try {
            String source = toSource(path);
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return new Document(source, content);
        } catch (IOException e) {
            warn("读取失败，跳过", path, e);
            return null;
        }
    }

    /**
     *  .pdf:交给 Tika 抽文字 + 两道防御(空白/解析失败)
     */
    private Document loadPdf(Path path) {
        String source = toSource(path);

        try {
            String text = tika.parseToString(path.toFile()); // PDF → 纯文本
            // 防御 A:扫描版 PDF(纯图片)抽出空白 —— 报警跳过,别把空文本塞进流水线
            if (text == null || text.isBlank()) {
                warn("疑似扫描版 PDF(抽不出文字),跳过", path, null);
                return null;
            }
            String heading = stripExt(path.getFileName().toString());
            String content = "#" + heading + "\n\n" + text;
            return new Document(source, content);
        } catch (Exception e) {
            // 防御 B:加密 / 损坏 / 假 PDF(TikaException、IOException 都在这里兜住)
            warn("PDF 解析失败,跳过", path, e);
            return null;
        }
    }

    /**
     * source 用相对 docsDir 的路径;Windows 分隔符统一成 /
     */
    private String toSource(Path path) {
        return docsDir.relativize(path).toString().replace('\\', '/');
    }

    /** 去掉扩展名:"手册.pdf" → "手册"(用作 PDF 的 heading) */
    private String stripExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     *  跳过要喊出来 —— 沉默地丢文档是批量任务最讨厌的毛病
     */
    private void warn(String message, Path path, Exception e) {
        String reason = (e == null) ? "" : " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
        log.error("[警告] {}: {} {}", message, toSource(path), reason, e);
    }
}
