package com.nuaa.ragagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.entity.KbSpace;
import com.nuaa.ragagent.enums.DocumentStatus;
import com.nuaa.ragagent.enums.TaskType;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.mapper.KbChunkMapper;
import com.nuaa.ragagent.mapper.KbDocumentMapper;
import com.nuaa.ragagent.mapper.KbSpaceMapper;
import com.nuaa.ragagent.response.ReconcileResult;
import com.nuaa.ragagent.service.IndexReconciliationService;
import com.nuaa.ragagent.service.IndexTaskService;
import com.nuaa.ragagent.service.KeywordIndexService;
import com.nuaa.ragagent.service.KnowledgeEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
/**
 * @author jiyunhe
 */

@Service
public class IndexReconciliationServiceImpl implements IndexReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(IndexReconciliationServiceImpl.class);

    private static final int STATUS_NORMAL = 1;

    private final KbChunkMapper kbChunkMapper;

    private final KbDocumentMapper kbDocumentMapper;

    private final KbSpaceMapper kbSpaceMapper;

    private final KnowledgeEmbeddingService knowledgeEmbeddingService;

    private final KeywordIndexService keywordIndexService;

    private final IndexTaskService indexTaskService;

    public IndexReconciliationServiceImpl(KbChunkMapper kbChunkMapper,
                                          KbDocumentMapper kbDocumentMapper,
                                          KbSpaceMapper kbSpaceMapper,
                                          KnowledgeEmbeddingService knowledgeEmbeddingService,
                                          KeywordIndexService keywordIndexService,
                                          IndexTaskService indexTaskService) {
        this.kbChunkMapper = kbChunkMapper;
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbSpaceMapper = kbSpaceMapper;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
        this.keywordIndexService = keywordIndexService;
        this.indexTaskService = indexTaskService;
    }

    @Override
    public ReconcileResult reconcile() {
        long start = System.currentTimeMillis();

        // 有效 chunk 集合（非 INVALID 文档的 chunk）：其索引不属于孤儿
        Set<Long> validChunkIds = loadValidChunkIds();

        // 扫描并收集 Qdrant/ES 实际存在的 chunkId，清理孤儿
        Set<Long> qdrantExisting = new HashSet<>();
        Set<Long> esExisting = new HashSet<>();
        int qdrantOrphanCount = knowledgeEmbeddingService.reconcileOrphans(validChunkIds, qdrantExisting);
        int esOrphanCount = keywordIndexService.reconcileOrphans(validChunkIds, esExisting);

        // 缺失检测：ACTIVE 文档的 chunk 应全部存在于索引中（ACTIVE ⟺ 全部 chunk 构建成功）
        List<KbChunk> activeChunks = loadActiveChunks();
        Set<Long> expectedIds = activeChunks.stream().map(KbChunk::getId).collect(Collectors.toSet());

        Set<Long> missingInQdrant = new HashSet<>(expectedIds);
        missingInQdrant.removeAll(qdrantExisting);
        Set<Long> missingInEs = new HashSet<>(expectedIds);
        missingInEs.removeAll(esExisting);
        // 任一索引缺失都需要补建：双写不是原子操作，不能只判断两边是否同时缺失
        Set<Long> missingIds = new HashSet<>(missingInQdrant);
        missingIds.addAll(missingInEs);

        // 缺失 chunk 按文档聚合，登记 REPAIR_INDEX 任务（去重）
        Set<Long> repairDocumentIds = new HashSet<>();
        for (KbChunk chunk : activeChunks) {
            if (missingIds.contains(chunk.getId())) {
                repairDocumentIds.add(chunk.getDocumentId());
            }
        }
        int repairTaskInserted = 0;
        for (Long documentId : repairDocumentIds) {
            try {
                indexTaskService.createTask(documentId, TaskType.REPAIR_INDEX);
                repairTaskInserted++;
            } catch (Exception e) {
                log.warn("登记 REPAIR_INDEX 失败 documentId={}: {}", documentId, e.getMessage());
            }
        }

        ReconcileResult result = new ReconcileResult()
                .setQdrantScanned(qdrantExisting.size())
                .setEsScanned(esExisting.size())
                .setQdrantOrphanCount(qdrantOrphanCount)
                .setEsOrphanCount(esOrphanCount)
                .setMissingChunkCount(missingIds.size())
                .setRepairDocumentCount(repairDocumentIds.size())
                .setRepairTaskInsertedCount(repairTaskInserted)
                .setDurationMs(System.currentTimeMillis() - start);

        log.info("对账完成: Qdrant 孤儿 {} / ES 孤儿 {} / 缺失 chunk {} / 待补建文档 {} / 登记 REPAIR {} / 耗时 {}ms",
                qdrantOrphanCount, esOrphanCount, missingIds.size(),
                repairDocumentIds.size(), repairTaskInserted, result.getDurationMs());
        return result;
    }

    @Override
    public ReconcileResult reconcileByDocument(Long documentId) {
        long start = System.currentTimeMillis();

        KbDocument document = kbDocumentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException("document not found: " + documentId);
        }

        TaskType taskType = DocumentStatus.INVALID.getValue() == document.getStatus()
                ? TaskType.DELETE_INDEX
                : TaskType.REPAIR_INDEX;
        indexTaskService.createTask(documentId, taskType);

        return new ReconcileResult()
                .setRepairDocumentCount(1)
                .setRepairTaskInsertedCount(1)
                .setDurationMs(System.currentTimeMillis() - start);
    }

    /**
     * 周期对账兜底（默认每天 03:00，可通过 rag.reconciliation.cron 配置）。
     * 失败仅记录日志，不影响主流程。
     */
    @Scheduled(cron = "${rag.reconciliation.cron:0 0 3 * * *}")
    public void scheduledReconcile() {
        log.info("开始周期索引对账");
        try {
            reconcile();
        } catch (Exception e) {
            log.error("周期对账失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 构建当前有效 chunkId 集合（孤儿清理基准）。
     * <p>纳入所有非 INVALID 文档（ACTIVE/INDEXING/FAILED）的 chunk：INDEXING/FAILED 文档
     * 已写入索引的部分（如部分成功保留的数据）不能被对账误删，只有 INVALID 文档的索引才是孤儿。</p>
     */
    private Set<Long> loadValidChunkIds() {
        List<Long> validDocumentIds = kbDocumentMapper.selectList(
                        new LambdaQueryWrapper<KbDocument>()
                                .ne(KbDocument::getStatus, DocumentStatus.INVALID.getValue()))
                .stream().map(KbDocument::getId).toList();

        List<Long> validSpaceIds = kbSpaceMapper.selectList(
                        new LambdaQueryWrapper<KbSpace>().eq(KbSpace::getStatus, STATUS_NORMAL))
                .stream().map(KbSpace::getId).toList();

        if (validDocumentIds.isEmpty() || validSpaceIds.isEmpty()) {
            // TODO(known-issue): 有效集合为空时返回空集，会被 reconcileOrphans 当作“索引里全是孤儿”
            //  而删光 Qdrant/ES 中所有带 chunk_id 的数据 —— 兜底动作反而变成高危操作（例如空间表被误清、
            //  查询条件写错时会触发）。修复方向：此处改为抛出/返回“跳过对账”信号并告警，而不是继续清理。
            return Set.of();
        }

        return kbChunkMapper.selectList(
                        new LambdaQueryWrapper<KbChunk>()
                                .eq(KbChunk::getStatus, STATUS_NORMAL)
                                .in(KbChunk::getDocumentId, validDocumentIds)
                                .in(KbChunk::getSpaceId, validSpaceIds))
                .stream().map(KbChunk::getId).collect(Collectors.toSet());
    }

    /**
     * 加载 ACTIVE 文档的正常 chunk 列表（缺失检测基准）。
     * <p>仅 ACTIVE 文档参与修复：INDEXING 正在构建、FAILED 会重试，均不应触发 REPAIR 与其竞争。</p>
     */
    private List<KbChunk> loadActiveChunks() {
        List<Long> activeDocumentIds = kbDocumentMapper.selectList(
                        new LambdaQueryWrapper<KbDocument>()
                                .eq(KbDocument::getStatus, DocumentStatus.ACTIVE.getValue()))
                .stream().map(KbDocument::getId).toList();

        List<Long> validSpaceIds = kbSpaceMapper.selectList(
                        new LambdaQueryWrapper<KbSpace>().eq(KbSpace::getStatus, STATUS_NORMAL))
                .stream().map(KbSpace::getId).toList();

        if (activeDocumentIds.isEmpty() || validSpaceIds.isEmpty()) {
            return List.of();
        }

        return kbChunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getStatus, STATUS_NORMAL)
                        .in(KbChunk::getDocumentId, activeDocumentIds)
                        .in(KbChunk::getSpaceId, validSpaceIds));
    }
}
