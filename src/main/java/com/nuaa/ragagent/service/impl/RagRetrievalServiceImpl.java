package com.nuaa.ragagent.service.impl;

import com.nuaa.ragagent.enums.DocumentStatus;
import com.nuaa.ragagent.enums.RetrievalMode;
import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.entity.KbSpace;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.mapper.KbChunkMapper;
import com.nuaa.ragagent.mapper.KbDocumentMapper;
import com.nuaa.ragagent.mapper.KbSpaceMapper;
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.service.KeywordIndexService;
import com.nuaa.ragagent.service.KnowledgeEmbeddingService;
import com.nuaa.ragagent.service.RagRetrievalService;
import com.nuaa.ragagent.service.RerankService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
/**
 * @author jiyunhe
 */

@Service
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int DEFAULT_CANDIDATE_K = 20;

    /** 混合检索加重排：送入重排模型的候选数上限（默认值，可通过 rag.retrieval.rerank-candidate-top-m 覆盖） */
    private static final int DEFAULT_RERANK_CANDIDATE_TOP_M = 30;

    /** 混合检索：最终返回条数（未显式传 topK 时） */
    private static final int FINAL_TOP_K = 10;

    /** RRF（Reciprocal Rank Fusion）平滑常数 */
    private static final int RRF_K = 60;

    private static final int STATUS_NORMAL = 1;

    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final KeywordIndexService keywordIndexService;
    private final RerankService rerankService;
    private final KbChunkMapper kbChunkMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbSpaceMapper kbSpaceMapper;

    /** 送入重排模型的候选数上限，可配置以便作为评测变量 */
    private final int rerankCandidateTopM;

    public RagRetrievalServiceImpl(KnowledgeEmbeddingService knowledgeEmbeddingService,
                                   KeywordIndexService keywordIndexService,
                                   RerankService rerankService,
                                   KbChunkMapper kbChunkMapper,
                                   KbDocumentMapper kbDocumentMapper,
                                   KbSpaceMapper kbSpaceMapper,
                                   @Value("${rag.retrieval.rerank-candidate-top-m:30}") int rerankCandidateTopM) {
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
        this.keywordIndexService = keywordIndexService;
        this.rerankService = rerankService;
        this.kbChunkMapper = kbChunkMapper;
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbSpaceMapper = kbSpaceMapper;
        this.rerankCandidateTopM = rerankCandidateTopM > 0
                ? rerankCandidateTopM
                : DEFAULT_RERANK_CANDIDATE_TOP_M;
    }

    @Override
    public List<SearchChunkResponse> search(SearchChunksRequest request) {
        validateRequest(request);

        RetrievalMode mode = RetrievalMode.from(request.getRetrievalMode());

        List<SearchChunkResponse> results = switch (mode) {
            case KEYWORD_ONLY -> searchKeywordOnly(request);
            case HYBRID -> searchHybrid(request, false);
            case HYBRID_RERANK -> searchHybrid(request, true);
            default -> searchVectorOnly(request);
        };

        // 最终有效性校验：候选出来后以 MySQL 为准，chunk 存在且其 Document/Space 均有效
        return filterValidChunks(results);
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
     *
     * <p>每路召回深度由请求的 candidateK 决定（不再固定常量），使其可作为评测变量；
     * 送入重排模型的候选数上限由 rag.retrieval.rerank-candidate-top-m 配置，与 candidateK 解耦。</p>
     */
    private List<SearchChunkResponse> searchHybrid(SearchChunksRequest request, boolean rerank) {
        int finalTopK = resolveFinalTopK(request);
        int candidateK = Math.max(resolveTopK(request), resolveCandidateK(request));

        SearchChunksRequest candidateRequest = copyForCandidateSearch(request, candidateK);

        List<SearchChunkResponse> vectorResults = searchVectorOnly(candidateRequest);
        List<SearchChunkResponse> keywordResults = searchKeywordCandidates(candidateRequest, candidateK);

        List<SearchChunkResponse> fused = rrfFuse(vectorResults, keywordResults);

        if (!rerank) {
            return fused.stream()
                    .limit(finalTopK)
                    .toList();
        }

        // 重排输入量取配置上限与 finalTopK 的较大者，避免 M < topK 时静默少返回
        int rerankInputLimit = Math.max(rerankCandidateTopM, finalTopK);
        List<SearchChunkResponse> candidates = fused.stream()
                .limit(rerankInputLimit)
                .toList();

        return rerankService.rerank(request.getQuery(), candidates, finalTopK);
    }

    /**
     * 复制一份用于候选召回的请求，把 topK 与 candidateK 统一为给定的每路召回深度
     * （向量侧只读 topK，关键词侧只读 limit）。
     */
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

    private boolean isNormal(Integer status) {
        return status != null && STATUS_NORMAL == status;
    }

    private Long getChunkId(SearchChunkResponse response) {
        if (response == null) {
            return null;
        }

        return response.getChunkId();
    }

    /**
     * 以 MySQL 为准对候选结果做最终有效性校验（保持原顺序）：
     * chunk 当前仍存在（status=1），且其 Document/Space 均处于有效状态，
     * 且 chunk 的 document_id/space_id 与归属一致。
     */
    private List<SearchChunkResponse> filterValidChunks(List<SearchChunkResponse> results) {
        if (CollectionUtils.isEmpty(results)) {
            return results;
        }

        List<Long> chunkIds = results.stream()
                .map(SearchChunkResponse::getChunkId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (chunkIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<KbChunk> chunks = kbChunkMapper.selectBatchIds(chunkIds);
        Map<Long, KbChunk> chunkMap = chunks.stream()
                .filter(c -> c != null && isNormal(c.getStatus()))
                .collect(Collectors.toMap(KbChunk::getId, Function.identity()));

        List<Long> documentIds = chunkMap.values().stream()
                .map(KbChunk::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        // 检索只认已生效（ACTIVE）文档：INDEXING/FAILED 未生效不返回
        Map<Long, KbDocument> documentMap = documentIds.isEmpty() ? Map.of()
                : kbDocumentMapper.selectBatchIds(documentIds).stream()
                        .filter(d -> d != null && DocumentStatus.ACTIVE.getValue() == d.getStatus())
                        .collect(Collectors.toMap(KbDocument::getId, Function.identity()));

        List<Long> spaceIds = chunkMap.values().stream()
                .map(KbChunk::getSpaceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, KbSpace> spaceMap = spaceIds.isEmpty() ? Map.of()
                : kbSpaceMapper.selectBatchIds(spaceIds).stream()
                        .filter(s -> s != null && isNormal(s.getStatus()))
                        .collect(Collectors.toMap(KbSpace::getId, Function.identity()));

        return results.stream()
                .filter(r -> {
                    KbChunk chunk = chunkMap.get(r.getChunkId());
                    if (chunk == null) {
                        return false;
                    }

                    KbDocument document = documentMap.get(chunk.getDocumentId());
                    if (document == null || !chunk.getSpaceId().equals(document.getSpaceId())) {
                        return false;
                    }

                    return spaceMap.containsKey(chunk.getSpaceId());
                })
                .toList();
    }
}
