package com.nuaa.ragagent.explore;

import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 探索性测试：用 commonmark-java 解析数据集全部 markdown，输出 AST 结构。
 * 仅用于观察结构，不参与正式断言。
 */
class MarkdownAstExploreTest {

    private static final Path MARKDOWN_DIR =
            Paths.get("dataset", "mybatis-3.5.19-zh", "processed", "markdown");

    private static final Path DUMP_FILE =
            Paths.get("target", "explore", "markdown-ast-dump.txt");

    @Test
    void dumpAllMarkdownAst() throws IOException {
        List<Path> mdFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MARKDOWN_DIR, "*.md")) {
            for (Path md : stream) {
                mdFiles.add(md);
            }
        }
        mdFiles.sort(Path::compareTo);

        Parser parser = Parser.builder().build();
        List<String> lines = new ArrayList<>();
        for (Path md : mdFiles) {
            String source = Files.readString(md, StandardCharsets.UTF_8);
            Node document = parser.parse(stripFrontMatter(source));

            lines.add("");
            lines.add("======== " + md.getFileName() + " ========");
            dumpNode(document, 0, lines);
        }

        Files.createDirectories(DUMP_FILE.getParent());
        Files.write(DUMP_FILE, lines, StandardCharsets.UTF_8);
        lines.forEach(System.out::println);
        System.out.println();
        System.out.println("AST dump 已写入: " + DUMP_FILE.toAbsolutePath());
    }

    /** 去掉 YAML frontmatter（首尾 --- 包围块），只留正文。 */
    private String stripFrontMatter(String source) {
        if (source.startsWith("---")) {
            int end = source.indexOf("\n---", 3);
            if (end >= 0) {
                return source.substring(end + 4);
            }
        }
        return source;
    }

    private void dumpNode(Node node, int depth, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        sb.append(node.getClass().getSimpleName());

        if (node instanceof Heading heading) {
            sb.append(" [h").append(heading.getLevel()).append("]");
        } else if (node instanceof FencedCodeBlock codeBlock) {
            String info = codeBlock.getInfo() == null ? "" : codeBlock.getInfo();
            sb.append(" [lang=").append(info.isEmpty() ? "-" : info)
                    .append(", ").append(codeBlock.getLiteral().split("\n", -1).length).append(" lines]");
        } else if (node instanceof IndentedCodeBlock codeBlock) {
            sb.append(" [").append(codeBlock.getLiteral().split("\n", -1).length).append(" lines]");
        } else if (node instanceof Text text) {
            sb.append(": ").append(truncate(text.getLiteral(), 60));
        }

        lines.add(sb.toString());

        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            dumpNode(child, depth + 1, lines);
        }
    }

    private String truncate(String s, int max) {
        String oneLine = s.replace("\n", "\\n");
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }
}
