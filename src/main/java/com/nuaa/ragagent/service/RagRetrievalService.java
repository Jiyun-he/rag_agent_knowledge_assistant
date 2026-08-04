package com.nuaa.ragagent.service;

import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.SearchChunkResponse;

import java.util.List;

/**
 * RAG 检索服务接口。
 *
 * @author jiyunhe
 */
public interface RagRetrievalService {

    /**
     * 执行知识库检索。
     * <p>根据请求中的检索模式执行对应的召回策略：纯向量、纯关键词、混合检索或混合检索加重排，
     * 返回按最终得分降序排列的命中分块列表。</p>
     *
     * @param request 检索请求，包含空间 ID、查询语句、检索模式及参数，不能为空
     * @return 按最终得分从高到低排列的命中分块列表
     * @throws BusinessException 当请求为空、空间 ID 为空或查询语句为空时抛出
     */
    List<SearchChunkResponse> search(SearchChunksRequest request);
}
