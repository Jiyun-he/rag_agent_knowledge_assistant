package com.nuaa.ragagent.service;

import com.nuaa.ragagent.response.SearchChunkResponse;

import java.util.List;
/**
 * 重排服务：调用 bge-reranker-v2-m3 对候选结果按与查询的相关度重排。
 *
 * @author jiyunhe
 */
public interface RerankService {

    /**
     * 对候选结果重排，返回按相关度降序的 topK 条。
     *
     * @param query      检索问题
     * @param candidates 候选结果（须含 content）
     * @param topK       返回条数
     */
    List<SearchChunkResponse> rerank(String query, List<SearchChunkResponse> candidates, int topK);
}
