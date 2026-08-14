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
 * 输出每个文件的 chunk 数、各 chunk token 数与首个 chunk 预览。
 */
class StructureAwareDatasetExploreTest {

    private static final Path MARKDOWN_DIR =
            Paths.get("dataset", "mybatis-3.5.19-zh", "processed", "markdown");

    private static final Path DUMP_FILE =
            Paths.get("target", "explore", "structure-aware-chunks.txt");

    @Test
    void chunkAllDatasetDocuments() throws IOException {
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
        int totalChunks = 0;
        for (Path md : mdFiles) {
            String source = Files.readString(md, StandardCharsets.UTF_8);
            List<String> chunks = chunker.split(source);

            lines.add("");
            lines.add("======== " + md.getFileName() + " ========");
            lines.add("chunk 数: " + chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                String preview = chunk.replace("\n", "\\n");
                if (preview.length() > 120) {
                    preview = preview.substring(0, 120) + "...";
                }
                lines.add(String.format("  [%d] %4d token | %s", i, counter.count(chunk), preview));
            }
            totalChunks += chunks.size();
        }

        lines.add("");
        lines.add("======== 合计 ========");
        lines.add("全部 chunk 数: " + totalChunks);

        Files.createDirectories(DUMP_FILE.getParent());
        Files.write(DUMP_FILE, lines, StandardCharsets.UTF_8);
        System.out.println("结构感知切分结果已写入: " + DUMP_FILE.toAbsolutePath());
    }
}
