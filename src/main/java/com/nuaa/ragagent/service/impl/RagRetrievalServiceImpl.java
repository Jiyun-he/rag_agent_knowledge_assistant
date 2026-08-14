package com.nuaa.ragagent.service.impl;

import com.nuaa.ragagent.enums.RetrievalMode;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.service.KeywordIndexService;
import com.nuaa.ragagent.service.KnowledgeEmbeddingService;
import com.nuaa.ragagent.service.RagRetrievalService;
import com.nuaa.ragagent.service.RerankService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * @author jiyunhe
 */

@Service
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int DEFAULT_CANDIDATE_K = 20;

    /** 混合检索：每个检索器（向量、关键词）各自召回的候选数 */
    private static final int PER_RETRIEVER_TOP_N = 50;

    /** 混合检索加重排：送入重排模型的候选数 */
    private static final int RERANK_CANDIDATE_TOP_M = 30;

    /** 混合检索：最终返回条数（未显式传 topK 时） */
    private static final int FINAL_TOP_K = 10;

    /** RRF（Reciprocal Rank Fusion）平滑常数 */
    private static final int RRF_K = 60;

    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final KeywordIndexService keywordIndexService;
    private final RerankService rerankService;

    public RagRetrievalServiceImpl(KnowledgeEmbeddingService knowledgeEmbeddingService,
                                   KeywordIndexService keywordIndexService,
                                   RerankService rerankService) {
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
        this.keywordIndexService = keywordIndexService;
        this.rerankService = rerankService;
    }

    @Override
    public List<SearchChunkResponse> search(SearchChunksRequest request) {
        validateRequest(request);

        RetrievalMode mode = RetrievalMode.from(request.getRetrievalMode());

        return switch (mode) {
            case KEYWORD_ONLY -> searchKeywordOnly(request);
            case HYBRID -> searchHybrid(request, false);
            case HYBRID_RERANK -> searchHybrid(request, true);
            default -> searchVectorOnly(request);
        };
    }

    private void validateRequest(SearchChunksRequest request) {
        if (request == null) {
            throw new BusinessException("检索请求不能为空");
        }

        if (request.getSpaceId() == null) {
            throw new BusinessException("知识库空间ID不能为空");
        }

        if (!StringUtils.hasText(request.getQuery())) {
            throw new BusinessException("检索问题不能为空");
        }
    }

    private int resolveTopK(SearchChunksRequest request) {
        if (request.getTopK() == null || request.getTopK() <= 0) {
            return DEFAULT_TOP_K;
        }
        return request.getTopK();
    }

    private int resolveFinalTopK(SearchChunksRequest request) {
        if (request.getTopK() == null || request.getTopK() <= 0) {
            return FINAL_TOP_K;
        }
        return request.getTopK();
    }

    private int resolveCandidateK(SearchChunksRequest request) {
        if (request.getCandidateK() == null || request.getCandidateK() <= 0) {
            return Math.max(DEFAULT_CANDIDATE_K, resolveTopK(request));
        }
        return Math.max(request.getCandidateK(), resolveTopK(request));
    }

    /**
     * VECTOR_ONLY:
     * 纯向量检索，query -> embedding -> Qdrant search -> chunk response。
     */
    private List<SearchChunkResponse> searchVectorOnly(SearchChunksRequest request) {
        int topK = resolveTopK(request);

        SearchChunksRequest vectorRequest = new SearchChunksRequest();
        vectorRequest.setSpaceId(request.getSpaceId());
        vectorRequest.setQuery(request.getQuery());
        vectorRequest.setTopK(topK);

        List<SearchChunkResponse> results = knowledgeEmbeddingService.searchChunks(vectorRequest);

        if (results == null || results.isEmpty()) {
            return new ArrayList<>();
        }

        for (SearchChunkResponse response : results) {
            Double score = response.getScore();

            response.setVectorScore(score);
            response.setFinalScore(score);
            response.setRetrievalSource("VECTOR");

            if (response.getScore() == null) {
                response.setScore(score);
            }
        }

        return results;
    }

    /**
     * KEYWORD_ONLY:
     * 基于 Elasticsearch + smartcn 做关键词召回。
     */
    private List<SearchChunkResponse> searchKeywordOnly(SearchChunksRequest request) {
        int topK = resolveTopK(request);
        int candidateK = Math.max(topK, resolveCandidateK(request));

        List<SearchChunkResponse> candidates = searchKeywordCandidates(request, candidateK);

        return candidates.stream()
                .sorted(Comparator.comparing(SearchChunkResponse::getFinalScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(topK)
                .toList();
    }

    /**
     * HYBRID / HYBRID_RERANK:
     * 向量召回 + 关键词召回，RRF 融合去重；可选模型重排。
     */
    private List<SearchChunkResponse> searchHybrid(SearchChunksRequest request, boolean rerank) {
        int finalTopK = resolveFinalTopK(request);

        SearchChunksRequest candidateRequest = copyForCandidateSearch(request, PER_RETRIEVER_TOP_N);

        List<SearchChunkResponse> vectorResults = searchVectorOnly(candidateRequest);
        List<SearchChunkResponse> keywordResults = searchKeywordCandidates(candidateRequest, PER_RETRIEVER_TOP_N);

        List<SearchChunkResponse> fused = rrfFuse(vectorResults, keywordResults);

        if (!rerank) {
            return fused.stream()
                    .limit(finalTopK)
                    .toList();
        }

        List<SearchChunkResponse> candidates = fused.stream()
                .limit(RERANK_CANDIDATE_TOP_M)
                .toList();

        return rerankService.rerank(request.getQuery(), candidates, finalTopK);
    }

    private SearchChunksRequest copyForCandidateSearch(SearchChunksRequest request, int limit) {
        SearchChunksRequest copied = new SearchChunksRequest();
        copied.setSpaceId(request.getSpaceId());
        copied.setQuery(request.getQuery());
        copied.setTopK(limit);
        copied.setCandidateK(limit);
        copied.setRetrievalMode(request.getRetrievalMode());
        return copied;
    }

    /**
     * 基于 Elasticsearch 的关键词候选召回（smartcn 中文分词）。
     */
    private List<SearchChunkResponse> searchKeywordCandidates(SearchChunksRequest request, int limit) {
        if (!StringUtils.hasText(request.getQuery())) {
            return new ArrayList<>();
        }

        return keywordIndexService.search(request.getQuery(), request.getSpaceId(), limit);
    }

    /**
     * RRF（Reciprocal Rank Fusion）融合：
     * 每个检索器按排名贡献 1/(k + rank)，同文档跨路相加；保留各路原始分用于展示。
     */
    private List<SearchChunkResponse> rrfFuse(List<SearchChunkResponse> vectorResults,
                                              List<SearchChunkResponse> keywordResults) {
        Map<Long, SearchChunkResponse> merged = new LinkedHashMap<>();
        Map<Long, Double> rrfScores = new HashMap<>(vectorResults.size());

        for (int rank = 0; rank < vectorResults.size(); rank++) {
            SearchChunkResponse item = vectorResults.get(rank);
            Long chunkId = getChunkId(item);
            if (chunkId == null) {
                continue;
            }

            item.setRetrievalSource("VECTOR");
            rrfScores.merge(chunkId, 1.0 / (RRF_K + rank + 1), Double::sum);
            merged.put(chunkId, item);
        }

        for (int rank = 0; rank < keywordResults.size(); rank++) {
            SearchChunkResponse item = keywordResults.get(rank);
            Long chunkId = getChunkId(item);
            if (chunkId == null) {
                continue;
            }

            rrfScores.merge(chunkId, 1.0 / (RRF_K + rank + 1), Double::sum);

            SearchChunkResponse existing = merged.get(chunkId);
            if (existing == null) {
                item.setRetrievalSource("KEYWORD");
                merged.put(chunkId, item);
            } else {
                existing.setKeywordScore(item.getKeywordScore());
                existing.setRetrievalSource("BOTH");
            }
        }

        List<SearchChunkResponse> results = new ArrayList<>(merged.values());
        for (SearchChunkResponse item : results) {
            double rrfScore = rrfScores.getOrDefault(item.getChunkId(), 0.0);
            item.setHybridScore(rrfScore);
            item.setFinalScore(rrfScore);
            item.setScore(rrfScore);
        }

        results.sort(Comparator.comparing(SearchChunkResponse::getFinalScore,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return results;
    }

    private Long getChunkId(SearchChunkResponse response) {
        if (response == null) {
            return null;
        }

        return response.getChunkId();
    }
}
