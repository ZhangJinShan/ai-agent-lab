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

/**
 * @author zhangjinshan
 * @description: 文档加载器
 * @create: 2026/10/31
 **/
public class DocumentLoader {

    private static final String MD_SUFFIX = ".md";
    private static final Logger log = LoggerFactory.getLogger(DocumentLoader.class);

    private final Path docsDir;

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
                    .filter(path -> path.getFileName().toString().endsWith(MD_SUFFIX)) // 只要 .md 文件
                    .filter(path -> !path.getFileName().toString().startsWith(".")) // 过虑调隐藏文件
                    .sorted() // 决策: 排序保确定性
                    .map(this::toDocument)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    private Document toDocument(Path path){
        try {
            String source = docsDir.relativize(path).toString();// 相对路径
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                log.info("{} 是空文件，请核实", source);
                return null; // 跳过不做处理
            }
            return new Document(source, content);
        } catch (IOException e) {
            throw new RuntimeException("读取文档失败: " + path, e);
        }
    }
}
