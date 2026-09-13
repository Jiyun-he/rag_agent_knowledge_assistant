package com.nuaa.ragagent.service;

import com.nuaa.ragagent.response.ReconcileResult;

/**
 * 索引对账服务：以 MySQL 为基准，周期性清理 Qdrant/ES 孤儿索引数据，并对缺失索引的
 * 生效文档登记 REPAIR_INDEX 任务补建。
 * <p>即时任务（{@link IndexTaskService}）失败或漏删/漏建的数据由本服务兜底。</p>
 *
 * @author jiyunhe
 */
public interface IndexReconciliationService {

    /**
     * 全量对账：
     * <ol>
     *     <li>构建 MySQL 有效 chunk 集合，清理 Qdrant/ES 中不在集合内的孤儿数据</li>
     *     <li>对比 ACTIVE 文档的期望 chunk 与 Qdrant/ES 实际存在的 chunk，对缺失文档登记 REPAIR_INDEX 任务</li>
     * </ol>
     *
     * @return 对账结果统计
     */
    ReconcileResult reconcile();

    /**
     * 单文档对账：按文档状态登记对账任务（INVALID → DELETE_INDEX，其余 → REPAIR_INDEX），
     * 由 worker 异步执行（辅助/人工调用）。
     *
     * @param documentId 文档 ID
     * @return 对账结果统计
     */
    ReconcileResult reconcileByDocument(Long documentId);
}
