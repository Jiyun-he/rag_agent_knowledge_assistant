package com.nuaa.ragagent.service;

import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.IndexBuildResult;
import com.nuaa.ragagent.response.SearchChunkResponse;

import java.util.List;
import java.util.Set;

/**
 * 文档索引构建（Qdrant 向量 + ES 关键词双写）与向量检索服务接口。
 *
 * @author jiyunhe
 */
public interface KnowledgeEmbeddingService {

    /**
     * 为文档构建索引：将分块逐一双写 Qdrant 向量与 ES 关键词索引，并更新分块索引状态。
     * <p>非 force（BUILD_INDEX）：仅处理 embedding_status 为 0（待处理）/2（失败）的分块，
     * 已成功的保留不重复处理，天然支持失败重试的部分成功续传。</p>
     * <p>force（REPAIR_INDEX）：忽略 embedding_status，全量重写文档全部正常分块，用于对账补建。</p>
     *
     * @param documentId 需要构建索引的文档 ID，不能为空
     * @param force      是否强制全量重写（对账补建用）；false 时仅处理未成功分块
     * @return 索引构建结果统计，包含总块数、成功数、失败数等
     * @throws BusinessException 当 documentId 为空，或文档不存在/已失效时抛出
     */
    IndexBuildResult buildDocumentIndex(Long documentId, boolean force);

    /**
     * 基于向量相似度检索知识库分块。
     * <p>校验空间存在后，在向量库中按查询语句检索，过滤掉无效分块后返回命中的分块及相似度得分。</p>
     *
     * @param request 检索请求，包含空间 ID、查询语句、返回条数等，不能为空
     * @return 命中的分块列表，按相似度从高到低排列
     * @throws BusinessException 当空间不存在或已删除时抛出
     */
    List<SearchChunkResponse> searchChunks(SearchChunksRequest request);

    /**
     * 按文档 ID 物理清理向量库中该文档的全部向量。
     * <p>用于文档失效后的索引清理：通过 metadata 的 document_id 过滤删除，
     * 不依赖 chunk 表记录（文档失效后 chunk 可能已物理删除）。</p>
     *
     * @param documentId 文档 ID，不能为空
     */
    void deleteVectorsByDocumentId(Long documentId);

    /**
     * 对账：全量扫描集合中所有 point 的 chunk_id，清理不在有效集合中的孤儿向量（物理删除），
     * 同时将扫描到的全部 chunkId 收集到 {@code existingChunkIds} 供调用方做缺失检测。
     *
     * @param validChunkIds    MySQL 侧当前有效的 chunkId 集合
     * @param existingChunkIds 输出参数：本次扫描到的全部 chunkId（含孤儿）
     * @return 清理的孤儿 point 数量
     */
    int reconcileOrphans(Set<Long> validChunkIds, Set<Long> existingChunkIds);
}
