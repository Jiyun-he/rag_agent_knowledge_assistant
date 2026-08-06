package com.nuaa.ragagent.util;

import com.nuaa.ragagent.response.SearchChunkResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RagPromptBuilderTest {

    private final RagPromptBuilder builder = new RagPromptBuilder();

    @Test
    void buildPrompt_withNoChunks_injectsQuestionWithoutReference() {
        String prompt = builder.buildPrompt("测试问题", List.of());

        assertThat(prompt).contains("User Question:\n测试问题\n\n");
        assertThat(prompt).contains("Retrieved Context:");
        assertThat(prompt).contains("Answer Requirements:");
        assertThat(prompt).doesNotContain("\n[Reference 1]\n");
    }

    @Test
    void buildPrompt_withOneChunk_containsReferenceMetadataAndContent() {
        SearchChunkResponse chunk = chunk(10L, 1L, 3, "这是检索到的内容");

        String prompt = builder.buildPrompt("问题", List.of(chunk));

        assertThat(prompt).contains("[Reference 1]");
        assertThat(prompt).contains("Document ID: 1");
        assertThat(prompt).contains("Chunk ID: 10");
        assertThat(prompt).contains("Chunk Index: 3");
        assertThat(prompt).contains("Content:\n这是检索到的内容");
    }

    @Test
    void buildPrompt_withMultipleChunks_referencesInOrder() {
        SearchChunkResponse chunk1 = chunk(1L, 1L, 0, "第一段");
        SearchChunkResponse chunk2 = chunk(2L, 1L, 1, "第二段");

        String prompt = builder.buildPrompt("问题", List.of(chunk1, chunk2));

        assertThat(prompt.indexOf("[Reference 1]")).isLessThan(prompt.indexOf("[Reference 2]"));
    }

    @Test
    void buildPrompt_withNullOptionalFields_skipsMetadataLines() {
        SearchChunkResponse chunk = new SearchChunkResponse();
        chunk.setChunkId(5L);

        String prompt = builder.buildPrompt("问题", List.of(chunk));

        assertThat(prompt).contains("Chunk ID: 5");
        assertThat(prompt).doesNotContain("Document ID:");
        assertThat(prompt).doesNotContain("Chunk Index:");
    }

    @Test
    void buildPrompt_withLongContent_truncatesWithEllipsis() {
        String longContent = "a".repeat(3000);
        SearchChunkResponse chunk = chunk(1L, 1L, 0, longContent);

        String prompt = builder.buildPrompt("问题", List.of(chunk));

        assertThat(prompt).contains("a".repeat(2000) + "...");
        assertThat(prompt).doesNotContain("a".repeat(2001));
    }

    @Test
    void buildPrompt_withNullContent_doesNotThrow() {
        SearchChunkResponse chunk = chunk(1L, 1L, 0, null);

        assertThatCode(() -> builder.buildPrompt("问题", List.of(chunk)))
                .doesNotThrowAnyException();
    }

    private SearchChunkResponse chunk(Long chunkId, Long documentId, Integer chunkIndex, String content) {
        SearchChunkResponse chunk = new SearchChunkResponse();
        chunk.setChunkId(chunkId);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        return chunk;
    }
}
