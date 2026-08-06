package com.nuaa.ragagent.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    @Test
    void split_null_returnsEmpty() {
        TextChunker chunker = new TextChunker(500, 80);

        assertThat(chunker.split(null)).isEmpty();
    }

    @Test
    void split_blank_returnsEmpty() {
        TextChunker chunker = new TextChunker(500, 80);

        assertThat(chunker.split("  \n\t ")).isEmpty();
    }

    @Test
    void split_shortText_returnsSingleChunk() {
        TextChunker chunker = new TextChunker(500, 80);

        List<String> chunks = chunker.split("hello");

        assertThat(chunks).containsExactly("hello");
    }

    @Test
    void split_exactlyMaxSize_returnsSingleChunk() {
        TextChunker chunker = new TextChunker(100, 30);

        List<String> chunks = chunker.split(seq(100));

        assertThat(chunks).containsExactly(seq(100));
    }

    @Test
    void split_longText_createsOverlappingChunks() {
        TextChunker chunker = new TextChunker(100, 30);

        // 200 字符：0-99、70-169、140-199，后两段与前一段重叠 30 字符
        List<String> chunks = chunker.split(seq(200));

        assertThat(chunks).containsExactly(
                seq(100),
                seq(170).substring(70),
                seq(200).substring(140));
    }

    @Test
    void split_normalizesCrLfAndCrToLf() {
        TextChunker chunker = new TextChunker(100, 10);

        List<String> chunks = chunker.split("a\r\nb\rc");

        assertThat(chunks).containsExactly("a\nb\nc");
    }

    @Test
    void split_trimsInputText() {
        TextChunker chunker = new TextChunker(100, 10);

        List<String> chunks = chunker.split("  hello  ");

        assertThat(chunks).containsExactly("hello");
    }

    @Test
    void constructor_clampsOverlapToHalfMaxSize() {
        // overlap 100 超过 maxSize/2 = 50，应被钳制为 50
        TextChunker chunker = new TextChunker(100, 100);

        List<String> chunks = chunker.split(seq(150));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(1)).hasSize(100);
    }

    @Test
    void constructor_clampsMaxSizeToMin100() {
        // maxSize 50 被提升到 100，overlap 10 保持
        TextChunker chunker = new TextChunker(50, 10);

        List<String> chunks = chunker.split(seq(120));

        assertThat(chunks.get(0)).hasSize(100);
    }

    @Test
    void constructor_clampsNegativeOverlapToZero() {
        // overlap 为负被钳制为 0，第二个 chunk 应从 index 200 开始
        TextChunker chunker = new TextChunker(200, -5);

        List<String> chunks = chunker.split(seq(250));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(1).charAt(0)).isEqualTo('s');
    }

    /** 生成 a-z 循环的定长序列，便于按索引预测切分边界。 */
    private String seq(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        return sb.toString();
    }
}
