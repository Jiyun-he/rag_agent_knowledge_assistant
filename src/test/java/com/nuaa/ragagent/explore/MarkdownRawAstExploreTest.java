package com.nuaa.ragagent.explore;

import org.commonmark.node.Code;
import org.commonmark.node.Document;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
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
 * 原始 AST 探索：不省略任何节点字段，代码块/行内 Code/Text 全部打印 getLiteral() 原文。
 * 仅用于观察 commonmark 解析器的原生输出，不做正式断言。
 */
class MarkdownRawAstExploreTest {

    private static final Path MARKDOWN_DIR =
            Paths.get("dataset", "mybatis-3.5.19-zh", "processed", "markdown");

    private static final Path DUMP_FILE =
            Paths.get("target", "explore", "markdown-raw-ast-dump.txt");

    @Test
    void dumpRawAst() throws IOException {
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
        System.out.println("原始 AST dump 已写入: " + DUMP_FILE.toAbsolutePath());
        System.out.println("总行数: " + lines.size());
    }

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

        // 块级代码块：打印完整原文
        if (node instanceof FencedCodeBlock fcb) {
            String info = fcb.getInfo() == null ? "" : fcb.getInfo();
            sb.append(" [lang=").append(info.isEmpty() ? "-" : info).append("]")
                    .append(" literal=[").append(fcb.getLiteral()).append("]");
        } else if (node instanceof IndentedCodeBlock icb) {
            sb.append(" literal=[").append(icb.getLiteral()).append("]");
        }
        // 行内 Code / Text / SoftLineBreak：打印字面量
        else if (node instanceof Code c) {
            sb.append(" literal=[").append(c.getLiteral()).append("]");
        } else if (node instanceof Text t) {
            sb.append(" literal=[").append(t.getLiteral()).append("]");
        } else if (node instanceof SoftLineBreak) {
            sb.append(" (\\n)");
        } else if (node instanceof Heading h) {
            sb.append(" [h").append(h.getLevel()).append("]");
        }

        lines.add(sb.toString());

        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            dumpNode(child, depth + 1, lines);
        }
    }
}
