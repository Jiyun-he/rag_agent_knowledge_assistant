package com.nuaa.ragagent.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.service.RerankService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
/**
 * 基于 TEI（text-embeddings-inference）部署的 BAAI/bge-reranker-v2-m3 重排。
 * <p>调用 POST /rerank：{"query": ..., "texts": [...], "top_n": N}，
 * 返回顶层 JSON 数组 [{"index": i, "score": s}, ...]，按 score 降序。</p>
 *
 * @author jiyunhe
 */
@Service
public class RerankServiceImpl implements RerankService {

    private final RestClient restClient;

    public RerankServiceImpl(RestClient.Builder builder,
                             @Value("${rag.rerank.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public List<SearchChunkResponse> rerank(String query, List<SearchChunkResponse> candidates, int topK) {
        if (CollectionUtils.isEmpty(candidates)) {
            return new ArrayList<>();
        }

        int requestedTopN = Math.max(1, Math.min(topK, candidates.size()));

        List<String> texts = candidates.stream()
                .map(SearchChunkResponse::getContent)
                .toList();

        RerankRequest request = new RerankRequest(query, texts, requestedTopN, false);

        List<RerankResult> results;
        try {
            // TEI /rerank 返回顶层 JSON 数组：[{"index": i, "score": s}, ...]
            results = restClient.post()
                    .uri("/rerank")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (Exception e) {
            throw new BusinessException("重排服务调用失败: " + e.getMessage());
        }

        if (CollectionUtils.isEmpty(results)) {
            return new ArrayList<>();
        }

        List<SearchChunkResponse> ranked = new ArrayList<>();
        for (RerankResult result : results) {
            if (result.index() < 0 || result.index() >= candidates.size()) {
                continue;
            }

            SearchChunkResponse item = candidates.get(result.index());
            item.setRerankScore(result.score());
            item.setFinalScore(result.score());
            item.setScore(result.score());

            ranked.add(item);
        }

        return ranked;
    }

    /**
     * TEI /rerank 请求体。
     */
    private record RerankRequest(String query,
                                 List<String> texts,
                                 @JsonProperty("top_n") int topN,
                                 @JsonProperty("return_documents") boolean returnDocuments) {
    }

    private record RerankResult(int index, double score) {
    }
}
