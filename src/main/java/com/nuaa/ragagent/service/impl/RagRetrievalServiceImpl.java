package com.nuaa.ragagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.enums.RetrievalMode;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.mapper.KbChunkMapper;
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.service.KnowledgeEmbeddingService;
import com.nuaa.ragagent.service.RagRetrievalService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int DEFAULT_CANDIDATE_K = 20;
    private static final double DEFAULT_VECTOR_WEIGHT = 0.7;
    private static final double DEFAULT_KEYWORD_WEIGHT = 0.3;
    private static final int CHUNK_STATUS_NORMAL = 1;
    private static final int CHUNK_EMBEDDING_STATUS_SUCCESS = 1;

    private final KbChunkMapper kbChunkMapper;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;

    public RagRetrievalServiceImpl(KbChunkMapper kbChunkMapper,
                                   KnowledgeEmbeddingService knowledgeEmbeddingService) {
        this.kbChunkMapper = kbChunkMapper;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
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

    private int resolveCandidateK(SearchChunksRequest request) {
        if (request.getCandidateK() == null || request.getCandidateK() <= 0) {
            return Math.max(DEFAULT_CANDIDATE_K, resolveTopK(request));
        }
        return Math.max(request.getCandidateK(), resolveTopK(request));
    }

    private double resolveVectorWeight(SearchChunksRequest request) {
        if (request.getVectorWeight() == null || request.getVectorWeight() < 0) {
            return DEFAULT_VECTOR_WEIGHT;
        }
        return request.getVectorWeight();
    }

    private double resolveKeywordWeight(SearchChunksRequest request) {
        if (request.getKeywordWeight() == null || request.getKeywordWeight() < 0) {
            return DEFAULT_KEYWORD_WEIGHT;
        }
        return request.getKeywordWeight();
    }

    /**
     * VECTOR_ONLY:
     * 这里需要迁移你原来 /api/rag/search 中已经能跑通的 Qdrant 向量检索逻辑。
     *
     * 迁移要求：
     * 1. 保持原来 query -> embedding -> Qdrant search -> chunk response 的流程。
     * 2. 返回 SearchChunkResponse。
     * 3. 给 vectorScore、finalScore、score 赋值。
     * 4. retrievalSource 设置为 VECTOR。
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
     * 第一版使用 MySQL + LIKE 做关键词召回。
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
     * 向量召回 + 关键词召回 + 去重融合 + 可选 rerank。
     */
    private List<SearchChunkResponse> searchHybrid(SearchChunksRequest request, boolean rerank) {
        int topK = resolveTopK(request);
        int candidateK = resolveCandidateK(request);

        double vectorWeight = resolveVectorWeight(request);
        double keywordWeight = resolveKeywordWeight(request);
        double totalWeight = vectorWeight + keywordWeight;

        if (totalWeight <= 0) {
            vectorWeight = DEFAULT_VECTOR_WEIGHT;
            keywordWeight = DEFAULT_KEYWORD_WEIGHT;
            totalWeight = vectorWeight + keywordWeight;
        }

        vectorWeight = vectorWeight / totalWeight;
        keywordWeight = keywordWeight / totalWeight;

        SearchChunksRequest candidateRequest = copyForCandidateSearch(request, candidateK);

        List<SearchChunkResponse> vectorResults = searchVectorOnly(candidateRequest);
        List<SearchChunkResponse> keywordResults = searchKeywordCandidates(candidateRequest, candidateK);

        Map<Long, SearchChunkResponse> merged = new LinkedHashMap<>();

        for (SearchChunkResponse item : vectorResults) {
            Long chunkId = getChunkId(item);
            if (chunkId == null) {
                continue;
            }

            item.setRetrievalSource("VECTOR");
            merged.put(chunkId, item);
        }

        for (SearchChunkResponse keywordItem : keywordResults) {
            Long chunkId = getChunkId(keywordItem);
            if (chunkId == null) {
                continue;
            }

            SearchChunkResponse existing = merged.get(chunkId);
            if (existing == null) {
                keywordItem.setRetrievalSource("KEYWORD");
                merged.put(chunkId, keywordItem);
            } else {
                existing.setKeywordScore(keywordItem.getKeywordScore());
                existing.setRetrievalSource("BOTH");
            }
        }

        List<SearchChunkResponse> mergedList = new ArrayList<>(merged.values());

        normalizeAndComputeHybridScore(mergedList, vectorWeight, keywordWeight);

        if (rerank) {
            applyRuleBasedRerank(request.getQuery(), mergedList);
        }

        return mergedList.stream()
                .sorted(Comparator.comparing(SearchChunkResponse::getFinalScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(topK)
                .toList();
    }

    private SearchChunksRequest copyForCandidateSearch(SearchChunksRequest request, int candidateK) {
        SearchChunksRequest copied = new SearchChunksRequest();
        copied.setSpaceId(request.getSpaceId());
        copied.setQuery(request.getQuery());
        copied.setTopK(candidateK);
        copied.setCandidateK(candidateK);
        copied.setRetrievalMode(request.getRetrievalMode());
        copied.setVectorWeight(request.getVectorWeight());
        copied.setKeywordWeight(request.getKeywordWeight());
        return copied;
    }

    /**
     * 基于 MySQL 的关键词候选召回。
     */
    private List<SearchChunkResponse> searchKeywordCandidates(SearchChunksRequest request, int limit) {
        List<String> terms = splitQueryTerms(request.getQuery());

        if (terms.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<KbChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbChunk::getSpaceId, request.getSpaceId());
        wrapper.eq(KbChunk::getStatus, CHUNK_STATUS_NORMAL);
        wrapper.eq(KbChunk::getEmbeddingStatus, CHUNK_EMBEDDING_STATUS_SUCCESS);

        /*
         * 如果你的 embeddingStatus 成功值不是 1，而是 2，
         * 这里要改成 eq(KbChunk::getEmbeddingStatus, 2)。
         *
         * 从你之前贴出的数据看，embedding_status 可能出现 2。
         * 所以你需要以当前项目中“向量化成功”的真实枚举值为准。
         */
        wrapper.and(w -> {
            for (int i = 0; i < terms.size(); i++) {
                String term = terms.get(i);
                if (i == 0) {
                    w.like(KbChunk::getContent, term);
                } else {
                    w.or().like(KbChunk::getContent, term);
                }
            }
        });

        wrapper.last("LIMIT " + limit);

        List<KbChunk> chunks = kbChunkMapper.selectList(wrapper);

        List<SearchChunkResponse> results = new ArrayList<>();
        for (KbChunk chunk : chunks) {
            double keywordScore = calculateKeywordScore(request.getQuery(), chunk.getContent());

            SearchChunkResponse response = toResponse(chunk);
            response.setKeywordScore(keywordScore);
            response.setFinalScore(keywordScore);
            response.setScore(keywordScore);
            response.setRetrievalSource("KEYWORD");

            results.add(response);
        }

        return results;
    }

    private List<String> splitQueryTerms(String query) {
        String normalized = query == null ? "" : query.trim();

        if (normalized.isEmpty()) {
            return new ArrayList<>();
        }

        String[] rawTerms = normalized.split("[\\s,，。；;：:、/\\\\()（）\\[\\]{}<>]+");

        List<String> terms = new ArrayList<>();

        for (String raw : rawTerms) {
            String term = raw.trim();
            if (term.isEmpty()) {
                continue;
            }

            if (term.length() > 50) {
                continue;
            }

            terms.add(term);
        }

        if (terms.isEmpty()) {
            terms.add(normalized);
        }

        return terms;
    }

    private double calculateKeywordScore(String query, String content) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(content)) {
            return 0.0;
        }

        List<String> terms = splitQueryTerms(query);
        if (terms.isEmpty()) {
            return 0.0;
        }

        String lowerContent = content.toLowerCase(Locale.ROOT);

        int hitCount = 0;
        int totalHitTimes = 0;

        for (String term : terms) {
            String lowerTerm = term.toLowerCase(Locale.ROOT);

            int count = countOccurrences(lowerContent, lowerTerm);
            if (count > 0) {
                hitCount++;
                totalHitTimes += count;
            }
        }

        double termHitRatio = (double) hitCount / terms.size();
        double frequencyBonus = Math.min(0.3, totalHitTimes * 0.03);

        return Math.min(1.0, termHitRatio + frequencyBonus);
    }

    private int countOccurrences(String text, String pattern) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(pattern)) {
            return 0;
        }

        int count = 0;
        int index = 0;

        while ((index = text.indexOf(pattern, index)) >= 0) {
            count++;
            index += pattern.length();
        }

        return count;
    }

    /**
     * 计算 hybridScore。
     */
    private void normalizeAndComputeHybridScore(
            List<SearchChunkResponse> items,
            double vectorWeight,
            double keywordWeight
    ) {
        double maxVectorScore = items.stream()
                .map(SearchChunkResponse::getVectorScore)
                .filter(score -> score != null && score > 0)
                .max(Double::compareTo)
                .orElse(1.0);

        double maxKeywordScore = items.stream()
                .map(SearchChunkResponse::getKeywordScore)
                .filter(score -> score != null && score > 0)
                .max(Double::compareTo)
                .orElse(1.0);

        for (SearchChunkResponse item : items) {
            double vectorScore = item.getVectorScore() == null ? 0.0 : item.getVectorScore();
            double keywordScore = item.getKeywordScore() == null ? 0.0 : item.getKeywordScore();

            double normalizedVectorScore = vectorScore <= 0 ? 0.0 : vectorScore / maxVectorScore;
            double normalizedKeywordScore = keywordScore <= 0 ? 0.0 : keywordScore / maxKeywordScore;

            double hybridScore = vectorWeight * normalizedVectorScore
                    + keywordWeight * normalizedKeywordScore;

            item.setHybridScore(hybridScore);
            item.setFinalScore(hybridScore);
            item.setScore(hybridScore);
        }
    }

    /**
     * 第一版规则 rerank:
     * 不是模型 rerank，而是工程上容易落地、容易解释的轻量重排序。
     */
    private void applyRuleBasedRerank(String query, List<SearchChunkResponse> items) {
        for (SearchChunkResponse item : items) {
            double hybridScore = item.getHybridScore() == null ? 0.0 : item.getHybridScore();

            double exactTermScore = calculateExactTermScore(query, item.getContent());
            double symbolScore = calculateSymbolScore(query, item.getContent());

            double rerankScore = 0.6 * hybridScore
                    + 0.25 * exactTermScore
                    + 0.15 * symbolScore;

            item.setRerankScore(rerankScore);
            item.setFinalScore(rerankScore);
            item.setScore(rerankScore);
        }
    }

    private double calculateExactTermScore(String query, String content) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(content)) {
            return 0.0;
        }

        List<String> terms = splitQueryTerms(query);
        if (terms.isEmpty()) {
            return 0.0;
        }

        String lowerContent = content.toLowerCase(Locale.ROOT);

        int exactHitCount = 0;

        for (String term : terms) {
            String lowerTerm = term.toLowerCase(Locale.ROOT);
            if (lowerContent.contains(lowerTerm)) {
                exactHitCount++;
            }
        }

        return (double) exactHitCount / terms.size();
    }

    /**
     * 对类名、方法名、接口名等技术符号做一个额外加分。
     * 例如 EmbeddingTaskService、/api/rag/search、vectorize-async。
     */
    private double calculateSymbolScore(String query, String content) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(content)) {
            return 0.0;
        }

        List<String> terms = splitQueryTerms(query);
        if (terms.isEmpty()) {
            return 0.0;
        }

        int symbolLikeTermCount = 0;
        int symbolHitCount = 0;

        for (String term : terms) {
            if (isSymbolLike(term)) {
                symbolLikeTermCount++;

                if (content.contains(term)) {
                    symbolHitCount++;
                }
            }
        }

        if (symbolLikeTermCount == 0) {
            return 0.0;
        }

        return (double) symbolHitCount / symbolLikeTermCount;
    }

    private boolean isSymbolLike(String term) {
        if (!StringUtils.hasText(term)) {
            return false;
        }

        return term.contains(".")
                || term.contains("/")
                || term.contains("-")
                || term.contains("_")
                || term.matches(".*[A-Z].*[a-z].*")
                || term.endsWith("Controller")
                || term.endsWith("Service")
                || term.endsWith("Mapper")
                || term.endsWith("Request")
                || term.endsWith("Response");
    }

    private SearchChunkResponse toResponse(KbChunk chunk) {
        SearchChunkResponse response = new SearchChunkResponse();

        response.setChunkId(chunk.getId());
        response.setDocumentId(chunk.getDocumentId());
        response.setSpaceId(chunk.getSpaceId());
        response.setChunkIndex(chunk.getChunkIndex());
        response.setContent(chunk.getContent());

        return response;
    }

    private Long getChunkId(SearchChunkResponse response) {
        if (response == null) {
            return null;
        }

        return response.getChunkId();
    }
}