package com.nuaa.ragagent.explore;

import com.nuaa.ragagent.util.StructureAwareChunker;
import com.nuaa.ragagent.util.TextChunker;
import com.nuaa.ragagent.util.TokenCounter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 探索性测试：对 MyBatis 数据集同时做 FIXED_SIZE 与 STRUCTURE_AWARE 两种切分，
 * 输出两套完整 chunk（不截断）与逐文档统计，用于设计评测 case 与人工标注期望 chunk。
 *
 * <p>切分源为 {@code processed/markdown/*.md}，并剥离文件顶部的 YAML front-matter——
 * 那 12 行是抓取元数据（documentKey / sourceUrl / contentHash 等），不属于文档正文，
 * 不剥离会被定长切分当作正文切进去。剥离后的正文与 {@code output/documents.jsonl}
 * 的 content 字段逐字符一致，因此这里标注出的 chunkIndex 与真实入库的 chunk 一一对应。</p>
 *
 * <p>参数取 application.yml 当前值：FIXED_SIZE max-size=500 / overlap-size=80；
 * STRUCTURE_AWARE target=800 / hard-limit=1200 / overlap=150 / context-max=100。</p>
 */
class ChunkingStrategyDumpTest {

    private static final Path MARKDOWN_DIR =
            Paths.get("dataset", "mybatis-3.5.19-zh", "processed", "markdown");

    private static final Path OUTPUT_DIR = Paths.get("target", "explore");

    /** YAML front-matter：文件开头 --- 到下一个 --- 之间的整段 */
    private static final Pattern FRONT_MATTER = Pattern.compile("^---\\n.*?\\n---\\n", Pattern.DOTALL);

    private static final int FIXED_MAX_SIZE = 500;
    private static final int FIXED_OVERLAP_SIZE = 80;
    private static final int SA_TARGET_SIZE = 800;
    private static final int SA_HARD_LIMIT = 1200;
    private static final int SA_OVERLAP_SIZE = 150;
    private static final int SA_CONTEXT_MAX = 100;

    @Test
    void dumpBothStrategies() throws IOException {
        List<Path> mdFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MARKDOWN_DIR, "*.md")) {
            for (Path md : stream) {
                mdFiles.add(md);
            }
        }
        mdFiles.sort(Path::compareTo);

        TokenCounter counter = new TokenCounter();
        TextChunker fixedChunker = new TextChunker(FIXED_MAX_SIZE, FIXED_OVERLAP_SIZE);
        StructureAwareChunker structureChunker = new StructureAwareChunker(
                SA_TARGET_SIZE, SA_HARD_LIMIT, SA_OVERLAP_SIZE, SA_CONTEXT_MAX, counter);

        List<String> fixedDump = new ArrayList<>();
        List<String> structureDump = new ArrayList<>();
        List<String> summary = new ArrayList<>();

        summary.add("documentKey,chars,fixedChunks,fixedTokens,structureChunks,structureTokens");
        int totalFixed = 0;
        int totalStructure = 0;

        for (Path md : mdFiles) {
            String key = md.getFileName().toString().replace(".md", "");
            String content = stripFrontMatter(Files.readString(md, StandardCharsets.UTF_8));

            List<String> fixedChunks = fixedChunker.split(content);
            List<String> structureChunks = structureChunker.split(content);

            int fixedTokens = fixedChunks.stream().mapToInt(counter::count).sum();
            int structureTokens = structureChunks.stream().mapToInt(counter::count).sum();
            totalFixed += fixedChunks.size();
            totalStructure += structureChunks.size();

            summary.add(String.format("%s,%d,%d,%d,%d,%d",
                    key, content.length(), fixedChunks.size(), fixedTokens,
                    structureChunks.size(), structureTokens));

            appendChunks(fixedDump, key, "FIXED_SIZE", fixedChunks, counter);
            appendChunks(structureDump, key, "STRUCTURE_AWARE", structureChunks, counter);
        }

        summary.add("");
        summary.add(String.format("TOTAL,,,,%d,,%d", totalFixed, totalStructure));

        Files.createDirectories(OUTPUT_DIR);
        write(OUTPUT_DIR.resolve("chunks-fixed-size.txt"), fixedDump);
        write(OUTPUT_DIR.resolve("chunks-structure-aware.txt"), structureDump);
        write(OUTPUT_DIR.resolve("chunking-strategy-summary.csv"), summary);

        System.out.println("FIXED_SIZE 总 chunk 数      = " + totalFixed);
        System.out.println("STRUCTURE_AWARE 总 chunk 数 = " + totalStructure);
        System.out.println("输出目录: " + OUTPUT_DIR.toAbsolutePath());
    }

    private String stripFrontMatter(String text) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        return FRONT_MATTER.matcher(normalized).replaceFirst("").stripLeading();
    }

    private void appendChunks(List<String> target, String docKey, String strategy,
                              List<String> chunks, TokenCounter counter) {
        target.add("############################################################");
        target.add("## " + docKey + "  [" + strategy + "]  " + chunks.size() + " chunks");
        target.add("############################################################");
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            target.add("");
            target.add("--------- chunkIndex=" + i + "  chars=" + chunk.length()
                    + "  tokens=" + counter.count(chunk) + " ---------");
            target.add(chunk);
        }
    }

    private void write(Path path, List<String> lines) throws IOException {
        Files.write(path, lines, StandardCharsets.UTF_8);
    }
}
