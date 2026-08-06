package com.nuaa.ragagent.service.impl;

import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.response.SearchChunkResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RerankServiceImplTest {

    private MockRestServiceServer server;

    private RerankServiceImpl service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new RerankServiceImpl(builder, "http://localhost:8081");
    }

    @Test
    void rerank_emptyCandidates_returnsEmptyWithoutHttpCall() {
        List<SearchChunkResponse> result = service.rerank("q", List.of(), 5);

        assertThat(result).isEmpty();
    }

    @Test
    void rerank_mapsResponseInDescendingScoreOrder() {
        List<SearchChunkResponse> candidates = List.of(
                candidate(0L, "c0"), candidate(1L, "c1"), candidate(2L, "c2"));

        server.expect(requestTo("http://localhost:8081/rerank"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.query", is("q")))
                .andExpect(jsonPath("$.texts[0]", is("c0")))
                .andExpect(jsonPath("$.texts[2]", is("c2")))
                .andExpect(jsonPath("$.top_n", is(2)))
                .andExpect(jsonPath("$.return_documents", is(false)))
                .andRespond(withSuccess(
                        "[{\"index\":2,\"score\":0.9},{\"index\":0,\"score\":0.5}]",
                        MediaType.APPLICATION_JSON));

        List<SearchChunkResponse> result = service.rerank("q", candidates, 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getChunkId()).isEqualTo(2L);
        assertThat(result.get(0).getRerankScore()).isEqualTo(0.9);
        assertThat(result.get(0).getFinalScore()).isEqualTo(0.9);
        assertThat(result.get(0).getScore()).isEqualTo(0.9);
        assertThat(result.get(1).getChunkId()).isEqualTo(0L);
        assertThat(result.get(1).getRerankScore()).isEqualTo(0.5);
        server.verify();
    }

    @Test
    void rerank_topKLargerThanCandidateCount_clampsTopN() {
        List<SearchChunkResponse> candidates = List.of(
                candidate(0L, "c0"), candidate(1L, "c1"), candidate(2L, "c2"));

        server.expect(requestTo("http://localhost:8081/rerank"))
                .andExpect(jsonPath("$.top_n", is(3)))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.rerank("q", candidates, 10);

        server.verify();
    }

    @Test
    void rerank_nonPositiveTopK_clampsToOne() {
        List<SearchChunkResponse> candidates = List.of(candidate(0L, "c0"));

        server.expect(requestTo("http://localhost:8081/rerank"))
                .andExpect(jsonPath("$.top_n", is(1)))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.rerank("q", candidates, 0);

        server.verify();
    }

    @Test
    void rerank_outOfRangeIndex_isSkipped() {
        List<SearchChunkResponse> candidates = List.of(
                candidate(0L, "c0"), candidate(1L, "c1"), candidate(2L, "c2"));

        server.expect(requestTo("http://localhost:8081/rerank"))
                .andRespond(withSuccess("[{\"index\":5,\"score\":1.0}]",
                        MediaType.APPLICATION_JSON));

        List<SearchChunkResponse> result = service.rerank("q", candidates, 3);

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    void rerank_emptyResults_returnsEmpty() {
        List<SearchChunkResponse> candidates = List.of(candidate(0L, "c0"));

        server.expect(requestTo("http://localhost:8081/rerank"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<SearchChunkResponse> result = service.rerank("q", candidates, 1);

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    void rerank_httpError_wrapsBusinessException() {
        List<SearchChunkResponse> candidates = List.of(candidate(0L, "c0"));

        server.expect(requestTo("http://localhost:8081/rerank"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.rerank("q", candidates, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重排服务调用失败");
        server.verify();
    }

    private SearchChunkResponse candidate(Long chunkId, String content) {
        SearchChunkResponse item = new SearchChunkResponse();
        item.setChunkId(chunkId);
        item.setContent(content);
        return item;
    }
}
