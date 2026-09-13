package com.nuaa.ragagent.service;

/**
 * 索引即时清理服务：物理清理文档在 Qdrant 与 ES 中的索引。
 * <p>作为 DELETE_INDEX 任务的执行体被 {@link IndexTaskService} 调用；清理失败由任务框架
 * 统一重试，最终仍失败交由周期对账 {@link IndexReconciliationService} 兜底。</p>
 *
 * @author jiyunhe
 */
public interface IndexCleanupService {

    /**
     * 按文档 ID 清理该文档在 Qdrant 与 ES 中的全部索引数据（幂等，失败由任务框架重试）。
     *
     * @param documentId 已失效的文档 ID
     */
    void cleanupDocumentIndexes(Long documentId);
}
