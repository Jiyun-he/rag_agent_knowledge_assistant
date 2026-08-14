package com.nuaa.ragagent.explore;

import com.nuaa.ragagent.util.StructureAwareChunker;
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

/**
 * 探索性测试：用默认参数（800/1200/150/100）对 MyBatis 数据集全部 md 做结构感知切分，
 * 输出每个 chunk 的完整内容（不截断）到 target/explore/structure-aware-chunks-full.txt。
 */
class StructureAwareChunksFullOutputTest {

    private static final Path MARKDOWN_DIR =
            Paths.get("dataset", "mybatis-3.5.19-zh", "processed", "markdown");

    private static final Path DUMP_FILE =
            Paths.get("target", "explore", "structure-aware-chunks-full.txt");

    @Test
    void dumpFullChunks() throws IOException {
        List<Path> mdFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MARKDOWN_DIR, "*.md")) {
            for (Path md : stream) {
                mdFiles.add(md);
            }
        }
        mdFiles.sort(Path::compareTo);

        TokenCounter counter = new TokenCounter();
        StructureAwareChunker chunker = new StructureAwareChunker(800, 1200, 150, 100, counter);

        List<String> lines = new ArrayList<>();
        for (Path md : mdFiles) {
            String source = Files.readString(md, StandardCharsets.UTF_8);
            List<String> chunks = chunker.split(source);

            lines.add("############################################################");
            lines.add("## " + md.getFileName() + "   (" + chunks.size() + " chunks)");
            lines.add("############################################################");
            for (int i = 0; i < chunks.size(); i++) {
                lines.add("");
                lines.add("========= chunk " + (i + 1) + "/" + chunks.size()
                        + "  [" + counter.count(chunks.get(i)) + " tokens] =========");
                lines.add(chunks.get(i));
            }
        }

        Files.createDirectories(DUMP_FILE.getParent());
        Files.write(DUMP_FILE, lines, StandardCharsets.UTF_8);
        System.out.println("完整 chunk 已写入: " + DUMP_FILE.toAbsolutePath());
        System.out.println("总行数: " + lines.size());
    }
}
