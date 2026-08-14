package com.nuaa.ragagent.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结构感知切分策略单测。构造参数取不小于构造钳制下限的值（targetSize>=100、hardLimit>=targetSize），
 * 使 targetSize=100 / hardLimit=200 / overlap=20 / contextMax=10 生效。
 */
class StructureAwareChunkerTest {

    private final TokenCounter counter = new TokenCounter();

    private StructureAwareChunker chunker() {
        return new StructureAwareChunker(100, 200, 20, 15, counter);
    }

    // 每段约 10~15 token
    private static final String P1 =
            "This is a short paragraph about MyBatis SQL mapping and configuration.";
    private static final String P2 =
            "Another short paragraph introducing the resultMap element and its attributes.";
    private static final String P3 =
            "A third paragraph covering dynamic SQL and the foreach element usage examples.";

    @Test
    void split_null_returnsEmpty() {
        assertThat(chunker().split(null)).isEmpty();
    }

    @Test
    void split_blank_returnsEmpty() {
        assertThat(chunker().split("  \n\t ")).isEmpty();
    }

    @Test
    void split_shortDoc_returnsSingleChunk() {
        List<String> chunks = chunker().split("# Title\n\n" + P1);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).startsWith("# Title");
        assertThat(chunks.get(0)).contains(P1);
    }

    @Test
    void split_prependsHeadingPathToChunk() {
        List<String> chunks = chunker().split("## 配置\n\n" + P1);

        assertThat(chunks.get(0)).startsWith("## 配置\n\n");
        assertThat(chunks.get(0)).contains(P1);
    }

    @Test
    void split_sameHeadingPath_mergesParagraphs() {
        // 三段合计 < targetSize，应合并为一个 chunk
        String doc = "# Topic\n\n" + P1 + "\n\n" + P2 + "\n\n" + P3;
        List<String> chunks = chunker().split(doc);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains(P1, P2, P3);
    }

    @Test
    void split_manyParagraphs_splitsByTargetSize() {
        // 10 段合计远超 targetSize，应拆成多个 chunk，每个 chunk 不超过 hardLimit + 单段
        StringBuilder doc = new StringBuilder("# Topic\n\n");
        for (int i = 0; i < 10; i++) {
            doc.append(P1).append("\n\n");
        }
        List<String> chunks = chunker().split(doc.toString());

        assertThat(chunks.size()).isGreaterThan(1);
        for (String chunk : chunks) {
            assertThat(counter.count(chunk)).isLessThanOrEqualTo(220);
        }
    }

    @Test
    void split_oversizedParagraph_hardSplitsWithOverlap() {
        // 单个段落 token > hardLimit(200)，应硬切，chunk 数 >1 且相邻 chunk 有重叠
        String longPara = "word ".repeat(600); // 约 600 token
        List<String> chunks = chunker().split(longPara);

        assertThat(chunks.size()).isGreaterThan(1);
        // overlap：下一块应包含上一块末尾的内容
        String first = chunks.get(0);
        String second = chunks.get(1);
        String firstTail = first.substring(Math.max(0, first.length() - 20));
        assertThat(second).contains(firstTail);
    }

    @Test
    void split_codeBlock_keepsAdjacentContext() {
        // 代码块 + 前置/后置短段落（≤8 token）应合并到一个 chunk
        String doc = "Brief note.\n\n```xml\n<select id=\"find\"/>\n```\n\nShort tail.";
        List<String> chunks = chunker().split(doc);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("Brief note.", "<select id=\"find\"/>", "Short tail.");
    }

    @Test
    void split_consecutiveCodeBlocks_mergeIntoSingleGroup() {
        // 连续两个代码块应合并为一个 CodeGroup，一个 chunk
        String doc = "```java\nint a = 1;\n```\n\n```xml\n<settings/>\n```";
        List<String> chunks = chunker().split(doc);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("int a = 1;", "<settings/>");
    }

    @Test
    void split_table_keepsWholeTable() {
        String table = "| name | value |\n|---|---|\n| a | 1 |\n| b | 2 |";
        List<String> chunks = chunker().split(table);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("| name | value |", "| a | 1 |", "| b | 2 |");
    }

    @Test
    void split_largeTable_splitsByRow_andRepeatsHeader() {
        // 构造超过 hardLimit 的大表，应按 row 拆分，且每个拆分 chunk 都重复表头
        StringBuilder table = new StringBuilder("# Table\n\n| name | value |\n|---|---|\n");
        for (int i = 0; i < 60; i++) {
            table.append("| row").append(i).append(" | val").append(i).append(" |\n");
        }
        List<String> chunks = chunker().split(table.toString());

        assertThat(chunks.size()).isGreaterThan(1);
        for (String chunk : chunks) {
            assertThat(chunk).contains("| name | value |");
            assertThat(chunk).contains("| --- | --- |");
        }
        // 表头重复，而不只是第一块有
        assertThat(chunks.get(1)).contains("| name | value |");
    }

    @Test
    void split_list_keepsWholeList() {
        String list = "- first item\n- second item\n- third item";
        List<String> chunks = chunker().split(list);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("- first item", "- third item");
    }

    @Test
    void split_largeList_splitsByItem() {
        // 超 hardLimit 的列表按完整 ListItem 拆，每块尽量接近 targetSize
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            list.append("- item number ").append(i).append("\n");
        }
        List<String> chunks = chunker().split(list.toString());

        assertThat(chunks.size()).isGreaterThan(1);
        for (String chunk : chunks) {
            assertThat(counter.count(chunk)).isLessThanOrEqualTo(220);
        }
        // 不从一个 ListItem 中间拆：每个 chunk 的每行都是 - 开头
        for (String chunk : chunks) {
            String[] lines = chunk.split("\n");
            for (String line : lines) {
                assertThat(line.trim()).startsWith("- ");
            }
        }
    }

    @Test
    void split_stripsYamlFrontMatter() {
        String doc = "---\ntitle: test\ndataset: mybatis\n---\n\n# Topic\n\n" + P1;
        List<String> chunks = chunker().split(doc);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).doesNotContain("title: test");
        assertThat(chunks.get(0)).contains(P1);
    }

    @Test
    void split_doesNotMergeAcrossHeadingPaths() {
        // 两个不同 headingPath 下的内容不应合并到同一个 chunk
        String doc = "# A\n\n" + P1 + "\n\n## B\n\n" + P2;
        List<String> chunks = chunker().split(doc);

        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        assertThat(chunks.get(0)).startsWith("# A");
        assertThat(chunks.get(0)).doesNotContain(P2);
        // B 的完整 headingPath 是 "# A\n## B"
        assertThat(chunks.get(1)).startsWith("# A\n## B");
    }

    @Test
    void split_oversizedTable_keepsPrevAndNext() {
        // settings 表场景：超大表格超 hardLimit，其前置和后置短段落都不能丢
        String prev = "以下是设置表的说明。";
        StringBuilder table = new StringBuilder("| name | value |\n|---|---|\n");
        for (int i = 0; i < 60; i++) {
            table.append("| k").append(i).append(" | v").append(i).append(" |\n");
        }
        String next = "一个配置完整的 settings 元素的示例如下：";
        String doc = "# 配置\n## 设置（settings）\n\n" + prev + "\n\n" + table + "\n" + next;

        List<String> chunks = chunker().split(doc);

        // 前置段落单独成块
        assertThat(chunks.get(0)).contains(prev);
        // 表格被按 row 拆分
        assertThat(chunks).anyMatch(c -> c.contains("| name | value |"));
        // 后置段落仍保留在某个 chunk 中，且没有被丢
        assertThat(chunks).anyMatch(c -> c.contains(next));
        // 表格拆块全部重复表头
        long tableChunks = chunks.stream().filter(c -> c.contains("| name | value |")).count();
        assertThat(tableChunks).isGreaterThan(1);
    }

    @Test
    void split_bridgeBetweenSpecialBlocks() {
        // 小表 + 短段落 + 代码块：短段落应同时作为小表的 next 和代码块的 prev（桥复用），
        // 出现在两个 chunk 中，两侧结构都带上下文，不丢数据
        String smallTable = "| name | value |\n|---|---|\n| k0 | v0 |\n| k1 | v1 |\n| k2 | v2 |";
        String mid = "这里是中间的短段落。";
        String doc = "# 配置\n\n" + smallTable + "\n\n" + mid + "\n\n```xml\n<settings/>\n```";

        List<String> chunks = chunker().split(doc);

        // 短段落出现至少两次（一次作为表的 next，一次作为代码块的 prev）
        long midCount = chunks.stream().filter(c -> c.contains(mid)).count();
        assertThat(midCount).isGreaterThanOrEqualTo(2);
        // 代码块 chunk 里带上了 mid 作为 prev
        assertThat(chunks).anyMatch(c -> c.contains(mid) && c.contains("<settings/>"));
        // 表格 chunk 里也带上了 mid 作为 next
        assertThat(chunks).anyMatch(c -> c.contains(mid) && c.contains("| name | value |"));
    }

    @Test
    void split_doesNotBorrowAcrossHeadingPaths() {
        // 跨章节：前一个 headingPath 的段落不能被后一个 headingPath 的特殊块借作 prev
        String doc = "# A\n\n" + P1 + "\n\n## B\n\n```xml\n<settings/>\n```";
        List<String> chunks = chunker().split(doc);

        // 代码块 chunk 不应包含 P1（跨章节）
        String codeChunk = chunks.stream()
                .filter(c -> c.contains("<settings/>"))
                .findFirst()
                .orElseThrow();
        assertThat(codeChunk).doesNotContain(P1);
        // P1 应属于 A 章节的正文 chunk
        assertThat(chunks.get(0)).contains(P1);
    }
}
