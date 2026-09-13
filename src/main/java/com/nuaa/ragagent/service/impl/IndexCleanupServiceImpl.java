package com.nuaa.ragagent.service.impl;

import com.nuaa.ragagent.service.IndexCleanupService;
import com.nuaa.ragagent.service.KeywordIndexService;
import com.nuaa.ragagent.service.KnowledgeEmbeddingService;
import org.springframework.stereotype.Service;
/**
 * @author jiyunhe
 */

@Service
public class IndexCleanupServiceImpl implements IndexCleanupService {

    private final KnowledgeEmbeddingService knowledgeEmbeddingService;

    private final KeywordIndexService keywordIndexService;

    public IndexCleanupServiceImpl(KnowledgeEmbeddingService knowledgeEmbeddingService,
                                   KeywordIndexService keywordIndexService) {
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
        this.keywordIndexService = keywordIndexService;
    }

    /**
     * DELETE_INDEX 任务执行体：物理清理该文档在 Qdrant 与 ES 中的全部索引。
     * <p>两步均为幂等操作（按 document_id 过滤删除，无匹配即为空操作），失败由任务框架
     * 统一重试，这里不做内部重试。</p>
     */
    @Override
    public void cleanupDocumentIndexes(Long documentId) {
        if (documentId == null) {
            return;
        }
        knowledgeEmbeddingService.deleteVectorsByDocumentId(documentId);
        keywordIndexService.deleteByDocumentId(documentId);
    }
}
