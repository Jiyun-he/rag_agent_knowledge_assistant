package com.nuaa.ragagent.controller;

import com.nuaa.ragagent.common.ApiResponse;
import com.nuaa.ragagent.response.ReconcileResult;
import com.nuaa.ragagent.service.IndexReconciliationService;
import com.nuaa.ragagent.service.KeywordIndexService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * @author jiyunhe
 */
@RestController
@RequestMapping("/api/rag/index")
public class RagIndexController {

    private final KeywordIndexService keywordIndexService;

    private final IndexReconciliationService indexReconciliationService;

    public RagIndexController(KeywordIndexService keywordIndexService,
                              IndexReconciliationService indexReconciliationService) {
        this.keywordIndexService = keywordIndexService;
        this.indexReconciliationService = indexReconciliationService;
    }

    /**
     * 全量重建 Elasticsearch 关键词索引（历史数据迁移用）。
     */
    @PostMapping("/rebuild")
    public ApiResponse<String> rebuild() {
        keywordIndexService.rebuildAll();
        return ApiResponse.success("索引重建完成");
    }

    /**
     * 人工触发全量索引对账（兜底清理 Qdrant/ES 孤儿数据）。
     */
    @PostMapping("/reconcile")
    public ApiResponse<ReconcileResult> reconcile() {
        return ApiResponse.success(indexReconciliationService.reconcile());
    }

    /**
     * 人工触发单文档索引对账（幂等即时清理）。
     */
    @PostMapping("/documents/{documentId}/reconcile")
    public ApiResponse<ReconcileResult> reconcileByDocument(@PathVariable Long documentId) {
        return ApiResponse.success(indexReconciliationService.reconcileByDocument(documentId));
    }
}
