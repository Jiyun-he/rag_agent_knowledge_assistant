package com.nuaa.ragagent.explore;

import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
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
 * 代码块 token 长度分布统计：解析全部 md，提取所有代码块原文，
 * 按 cl100k_base 经验比例估算每个代码块的 token（ASCII 约 4 字符/token，中文约 1.5 字符/token），
 * 然后分桶统计分布。纯本地统计，不依赖外部服务。
 */
class CodeBlockStatsTest {

    private static final Path MARKDOWN_DIR =
            Paths.get("dataset", "mybatis-3.5.19-zh", "processed", "markdown");

    private static final Path DUMP_FILE =
            Paths.get("target", "explore", "codeblock-token-distribution.txt");

    // cl100k_base 经验比例
    private static final double ASCII_CHARS_PER_TOKEN = 4.0;
    private static final double CJK_CHARS_PER_TOKEN = 1.5;

    // 分桶边界（token 数）
    private static final int[] BUCKET_BOUNDS = {100, 200, 300, 500, 800, 1200, Integer.MAX_VALUE};
    private static final String[] BUCKET_LABELS = {"0-100", "100-200", "200-300", "300-500", "500-800", "800-1200", "1200+"};

    @Test
    void collectCodeBlockTokenDistribution() throws IOException {
        List<Path> mdFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MARKDOWN_DIR, "*.md")) {
            for (Path md : stream) {
                mdFiles.add(md);
            }
        }
        mdFiles.sort(Path::compareTo);

        Parser parser = Parser.builder().build();
        int[] bucketCounts = new int[BUCKET_BOUNDS.length];
        double[] bucketTokens = new double[BUCKET_BOUNDS.length];
        List<BlockInfo> allBlocks = new ArrayList<>();
        int totalBlocks = 0;
        double totalTokens = 0;

        for (Path md : mdFiles) {
            String source = Files.readString(md, StandardCharsets.UTF_8);
            Node document = parser.parse(stripFrontMatter(source));
            collectBlocks(document, md.getFileName().toString(), allBlocks);
        }

        for (BlockInfo block : allBlocks) {
            int idx = bucketFor(block.tokens);
            bucketCounts[idx]++;
            bucketTokens[idx] += block.tokens;
            totalBlocks++;
            totalTokens += block.tokens;
        }

        List<String> lines = new ArrayList<>();
        lines.add("======== 代码块 token 长度分布（全数据集 202 个代码块） ========");
        lines.add(String.format("%-10s %8s %8s %8s %10s",
                "token区间", "块数", "占比", "累计token", "token占比"));
        double cumulative = 0;
        for (int i = 0; i < BUCKET_BOUNDS.length; i++) {
            double pct = (double) bucketCounts[i] / totalBlocks * 100;
            double tPct = (double) bucketTokens[i] / totalTokens * 100;
            cumulative += pct;
            lines.add(String.format("%-10s %8d %7.1f%% %10.0f %9.1f%%  (累计 %5.1f%%)",
                    BUCKET_LABELS[i], bucketCounts[i], pct, bucketTokens[i], tPct, cumulative));
        }
        lines.add("");
        lines.add(String.format("合计: %d 块, %.0f token", totalBlocks, totalTokens));
        lines.add(String.format("估算口径: ASCII 约 %.0f 字符/token，中文约 %.1f 字符/token",
                ASCII_CHARS_PER_TOKEN, CJK_CHARS_PER_TOKEN));

        lines.add("");
        lines.add("======== 各代码块明细（按文件） ========");
        String currentFile = null;
        for (BlockInfo b : allBlocks) {
            if (!b.file.equals(currentFile)) {
                currentFile = b.file;
                lines.add("");
                lines.add("-- " + currentFile + " --");
            }
            lines.add(String.format("  lang=%s  %.0f token  %4d chars  %s",
                    b.lang, b.tokens, b.chars, b.preview));
        }

        Files.createDirectories(DUMP_FILE.getParent());
        Files.write(DUMP_FILE, lines, StandardCharsets.UTF_8);
        System.out.println("分布统计已写入: " + DUMP_FILE.toAbsolutePath());
        System.out.println("代码块总数: " + totalBlocks);
    }

    private void collectBlocks(Node node, String file, List<BlockInfo> out) {
        if (node instanceof FencedCodeBlock fcb) {
            String info = fcb.getInfo() == null ? "" : fcb.getInfo().trim();
            out.add(new BlockInfo(file, info.isEmpty() ? "-" : info, fcb.getLiteral()));
        } else if (node instanceof IndentedCodeBlock icb) {
            out.add(new BlockInfo(file, "-", icb.getLiteral()));
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            collectBlocks(child, file, out);
        }
    }

    private int bucketFor(double tokens) {
        for (int i = 0; i < BUCKET_BOUNDS.length; i++) {
            if (tokens < BUCKET_BOUNDS[i]) {
                return i;
            }
        }
        return BUCKET_BOUNDS.length - 1;
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

    private static class BlockInfo {
        final String file;
        final String lang;
        final int chars;
        final double tokens;
        final String preview;

        BlockInfo(String file, String lang, String literal) {
            this.file = file;
            this.lang = lang;
            this.chars = literal.length();

            int ascii = 0;
            int cjk = 0;
            for (int i = 0; i < literal.length(); i++) {
                char c = literal.charAt(i);
                if (c < 0x80) {
                    ascii++;
                } else {
                    cjk++;
                }
            }
            this.tokens = ascii / ASCII_CHARS_PER_TOKEN + cjk / CJK_CHARS_PER_TOKEN;

            String firstLine = literal.split("\n", -1)[0].trim();
            this.preview = firstLine.length() > 40 ? firstLine.substring(0, 40) + "..." : firstLine;
        }
    }
}
