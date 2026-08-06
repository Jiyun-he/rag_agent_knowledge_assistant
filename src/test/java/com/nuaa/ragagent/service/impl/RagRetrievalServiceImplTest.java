package com.nuaa.ragagent.service.impl;

import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.service.KeywordIndexService;
import com.nuaa.ragagent.service.KnowledgeEmbeddingService;
import com.nuaa.ragagent.service.RerankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceImplTest {

    @Mock
    private KnowledgeEmbeddingService embeddingService;

    @Mock
    private KeywordIndexService keywordIndexService;

    @Mock
    private RerankService rerankService;

    private RagRetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RagRetrievalServiceImpl(embeddingService, keywordIndexService, rerankService);
    }

    // ---------- 参数校验 ----------

    @Test
    void search_nullRequest_throws() {
        assertThatThrownBy(() -> service.search(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("检索请求不能为空");
    }

    @Test
    void search_missingSpaceId_throws() {
        assertThatThrownBy(() -> service.search(req(null, "q", "VECTOR_ONLY", 5, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识库空间ID不能为空");
    }

    @Test
    void search_blankQuery_throws() {
        assertThatThrownBy(() -> service.search(req(1L, "  ", "VECTOR_ONLY", 5, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("检索问题不能为空");
    }

    // ---------- VECTOR_ONLY ----------

    @Test
    void search_vectorOnly_setsScoresAndSource() {
        when(embeddingService.searchChunks(any())).thenReturn(List.of(vectorItem(1L, 0.8), vectorItem(2L, null)));

        List<SearchChunkResponse> result = service.search(req(1L, "q", "VECTOR_ONLY", 5, null));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getVectorScore()).isEqualTo(0.8);
        assertThat(result.get(0).getFinalScore()).isEqualTo(0.8);
        assertThat(result.get(0).getScore()).isEqualTo(0.8);
        assertThat(result.get(0).getRetrievalSource()).isEqualTo("VECTOR");
        assertThat(result.get(1).getRetrievalSource()).isEqualTo("VECTOR");
        assertThat(result.get(1).getVectorScore()).isNull();
    }

    @Test
    void search_vectorOnly_passesResolvedTopK() {
        when(embeddingService.searchChunks(any())).thenReturn(List.of(vectorItem(1L, 0.5)));

        service.search(req(1L, "q", "VECTOR_ONLY", 3, null));

        ArgumentCaptor<SearchChunksRequest> captor = ArgumentCaptor.forClass(SearchChunksRequest.class);
        verify(embeddingService).searchChunks(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(3);
    }

    @Test
    void search_vectorOnly_emptyResults_returnsEmpty() {
        when(embeddingService.searchChunks(any())).thenReturn(List.of());

        assertThat(service.search(req(1L, "q", "VECTOR_ONLY", 5, null))).isEmpty();
    }

    // ---------- KEYWORD_ONLY ----------

    @Test
    void search_keywordOnly_sortsByScoreAndLimits() {
        when(keywordIndexService.search(anyString(), eq(1L), anyInt())).thenReturn(List.of(
                keywordItem(5L, 0.3), keywordItem(2L, 0.9), keywordItem(9L, 0.5),
                keywordItem(1L, 0.7), keywordItem(3L, 0.1)));

        List<SearchChunkResponse> result = service.search(req(1L, "q", "KEYWORD_ONLY", 3, null));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(SearchChunkResponse::getChunkId).containsExactly(2L, 1L, 9L);
        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(keywordIndexService).search(eq("q"), eq(1L), limitCaptor.capture());
        assertThat(limitCaptor.getValue()).isEqualTo(20);
    }

    @Test
    void search_keywordOnly_candidateKBelowTopK_usesTopKAsLimit() {
        when(keywordIndexService.search(anyString(), eq(1L), anyInt())).thenReturn(List.of(keywordItem(1L, 0.5)));

        service.search(req(1L, "q", "KEYWORD_ONLY", 8, 3));

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(keywordIndexService).search(eq("q"), eq(1L), limitCaptor.capture());
        assertThat(limitCaptor.getValue()).isEqualTo(8);
    }

    // ---------- HYBRID / RRF ----------

    @Test
    void search_hybrid_rrfFusesDedupsAndMarksSources() {
        when(embeddingService.searchChunks(any())).thenReturn(List.of(
                vectorItem(1L, 0.5), vectorItem(2L, 0.4), vectorItem(3L, 0.3)));
        when(keywordIndexService.search(anyString(), any(), anyInt())).thenReturn(List.of(
                keywordItem(2L, 9.0), keywordItem(4L, 8.0)));

        List<SearchChunkResponse> result = service.search(req(1L, "q", "HYBRID", 3, null));

        // chunk2 = 1/62 + 1/61，chunk1 = 1/61，chunk4 = 1/62，chunk3 = 1/63
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getChunkId()).isEqualTo(2L);
        assertThat(result.get(0).getRetrievalSource()).isEqualTo("BOTH");
        assertThat(result.get(0).getKeywordScore()).isEqualTo(9.0);
        assertThat(result.get(0).getHybridScore()).isCloseTo(1.0 / 62 + 1.0 / 61, within(1e-6));

        assertThat(result.get(1).getChunkId()).isEqualTo(1L);
        assertThat(result.get(1).getRetrievalSource()).isEqualTo("VECTOR");
        assertThat(result.get(1).getHybridScore()).isCloseTo(1.0 / 61, within(1e-6));

        assertThat(result.get(2).getChunkId()).isEqualTo(4L);
        assertThat(result.get(2).getRetrievalSource()).isEqualTo("KEYWORD");
        assertThat(result.get(2).getHybridScore()).isCloseTo(1.0 / 62, within(1e-6));
    }

    // ---------- HYBRID_RERANK ----------

    @Test
    void search_hybridRerank_limitsCandidatesAndDelegatesToRerank() {
        List<SearchChunkResponse> manyVectors = java.util.stream.IntStream.rangeClosed(1, 35)
                .mapToObj(i -> vectorItem((long) i, 0.5))
                .toList();
        when(embeddingService.searchChunks(any())).thenReturn(manyVectors);
        when(keywordIndexService.search(anyString(), any(), anyInt())).thenReturn(List.of());
        when(rerankService.rerank(eq("q"), anyList(), eq(3))).thenReturn(List.of(vectorItem(9L, 1.0)));

        List<SearchChunkResponse> result = service.search(req(1L, "q", "HYBRID_RERANK", 3, null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChunkId()).isEqualTo(9L);

        ArgumentCaptor<List<SearchChunkResponse>> candidatesCaptor = ArgumentCaptor.forClass(List.class);
        verify(rerankService).rerank(eq("q"), candidatesCaptor.capture(), eq(3));
        assertThat(candidatesCaptor.getValue()).hasSize(30);
    }

    // ---------- 非法/空模式回退 ----------

    @Test
    void search_unknownMode_fallsBackToVector() {
        when(embeddingService.searchChunks(any())).thenReturn(List.of(vectorItem(1L, 0.5)));

        service.search(req(1L, "q", "UNKNOWN", 3, null));

        verify(embeddingService).searchChunks(any());
    }

    @Test
    void search_blankMode_fallsBackToVector() {
        when(embeddingService.searchChunks(any())).thenReturn(List.of(vectorItem(1L, 0.5)));

        service.search(req(1L, "q", null, 3, null));

        verify(embeddingService).searchChunks(any());
    }

    private SearchChunksRequest req(Long spaceId, String query, String mode, Integer topK, Integer candidateK) {
        SearchChunksRequest request = new SearchChunksRequest();
        request.setSpaceId(spaceId);
        request.setQuery(query);
        request.setRetrievalMode(mode);
        request.setTopK(topK);
        request.setCandidateK(candidateK);
        return request;
    }

    private SearchChunkResponse vectorItem(Long chunkId, Double score) {
        SearchChunkResponse item = new SearchChunkResponse();
        item.setChunkId(chunkId);
        item.setContent("content-" + chunkId);
        item.setScore(score);
        item.setFinalScore(score);
        return item;
    }

    private SearchChunkResponse keywordItem(Long chunkId, double keywordScore) {
        SearchChunkResponse item = new SearchChunkResponse();
        item.setChunkId(chunkId);
        item.setContent("content-" + chunkId);
        item.setKeywordScore(keywordScore);
        item.setFinalScore(keywordScore);
        item.setScore(keywordScore);
        return item;
    }
}
