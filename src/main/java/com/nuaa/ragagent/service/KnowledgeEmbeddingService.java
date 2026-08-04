package com.nuaa.ragagent.service;

import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.response.VectorizeDocumentResponse;

import java.util.List;

/**
 * 文档向量化与向量检索服务接口。
 *
 * @author jiyunhe
 */
public interface KnowledgeEmbeddingService {

    /**
     * 向量化文档。
     * <p>将文档中所有待向量化的分块逐一写入向量库，并将各分块标记为向量化成功或失败，
     * 最后汇总返回本次向量化的统计结果。</p>
     *
     * @param documentId 需要向量化的文档 ID，不能为空
     * @return 向量化结果统计，包含总块数、成功数、失败数等
     * @throws BusinessException 当 documentId 为空，或文档不存在/已删除时抛出
     */
    VectorizeDocumentResponse vectorizeDocument(Long documentId);

    /**
     * 基于向量相似度检索知识库分块。
     * <p>校验空间存在后，在向量库中按查询语句检索，过滤掉无效分块后返回命中的分块及相似度得分。</p>
     *
     * @param request 检索请求，包含空间 ID、查询语句、返回条数等，不能为空
     * @return 命中的分块列表，按相似度从高到低排列
     * @throws BusinessException 当空间不存在或已删除时抛出
     */
    List<SearchChunkResponse> searchChunks(SearchChunksRequest request);
}
