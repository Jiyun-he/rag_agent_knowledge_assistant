package com.nuaa.ragagent.service;

import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.response.SearchChunkResponse;

import java.util.List;
import java.util.Set;
/**
 * 关键词索引服务：封装 Elasticsearch 的写入、清理与检索。
 *
 * @author jiyunhe
 */
public interface KeywordIndexService {

    /**
     * 幂等创建索引（含 smartcn 中文分词 mapping），ES 不可用时抛错。
     */
    void ensureIndex();

    /**
     * 以 chunk 主键作为文档 _id 写入（幂等 upsert）。
     *
     * @param chunk 待写入的 chunk，仅使用 content 及 4 个 id 字段
     */
    void upsertChunk(KbChunk chunk);

    /**
     * 按文档 ID 删除该文档下的全部索引文档。
     *
     * @param documentId 文档 ID
     */
    void deleteByDocumentId(Long documentId);

    /**
     * 关键词检索：match content + 过滤 space_id，返回带 keywordScore 的结果。
     *
     * @param query   检索问题
     * @param spaceId 知识库空间 ID
     * @param limit   最多返回条数
     * @return 带 keywordScore 的检索结果列表，按相关度降序
     */
    List<SearchChunkResponse> search(String query, Long spaceId, int limit);

    /**
     * 全量重建索引：扫描 status=1 且 embedding_status=1 的 chunk 全部重写。
     */
    void rebuildAll();

    /**
     * 对账：全量扫描索引中的 chunkId，清理不在有效集合中的孤儿索引文档（物理删除），
     * 同时将扫描到的全部 chunkId 收集到 {@code existingChunkIds} 供调用方做缺失检测。
     *
     * @param validChunkIds    MySQL 侧当前有效的 chunkId 集合
     * @param existingChunkIds 输出参数：本次扫描到的全部 chunkId（含孤儿）
     * @return 清理的孤儿索引文档数量
     */
    int reconcileOrphans(Set<Long> validChunkIds, Set<Long> existingChunkIds);
}
