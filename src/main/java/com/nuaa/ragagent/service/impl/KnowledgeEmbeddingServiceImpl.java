package com.nuaa.ragagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.entity.KbSpace;
import com.nuaa.ragagent.enums.DocumentStatus;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.exception.IndexBuildAbortedException;
import com.nuaa.ragagent.mapper.KbChunkMapper;
import com.nuaa.ragagent.mapper.KbDocumentMapper;
import com.nuaa.ragagent.mapper.KbSpaceMapper;
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.IndexBuildResult;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.service.KnowledgeEmbeddingService;
import com.nuaa.ragagent.service.KeywordIndexService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import io.qdrant.client.grpc.Points.WithVectorsSelector;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
/**
 * @author jiyunhe
 */

@Service
public class KnowledgeEmbeddingServiceImpl implements KnowledgeEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEmbeddingServiceImpl.class);

    private static final Integer STATUS_NORMAL = 1;

    private static final Integer EMBEDDING_PENDING = 0;

    private static final Integer EMBEDDING_SUCCESS = 1;

    private static final Integer EMBEDDING_FAILED = 2;

    private static final Integer DEFAULT_TOP_K = 5;

    private static final Integer MIN_TOP_K = 1;

    /** 向量检索 topK 上限的默认值，可通过 rag.retrieval.vector-max-top-k 覆盖 */
    private static final int DEFAULT_MAX_TOP_K = 50;

    /** 对账时 Qdrant scroll 每批大小 */
    private static final int SCROLL_LIMIT = 100;

    /** 对账时单次 Qdrant 调用超时 */
    private static final long QDRANT_TIMEOUT_SECONDS = 30;

    private final KbSpaceMapper kbSpaceMapper;

    private final KbDocumentMapper kbDocumentMapper;

    private final KbChunkMapper kbChunkMapper;

    private final VectorStore vectorStore;

    private final KeywordIndexService keywordIndexService;

    private final String collectionName;

    /** 向量检索 topK 上限：必须覆盖 candidateK 的可用范围，否则 candidateK 在向量侧会被静默截断 */
    private final int maxTopK;

    public KnowledgeEmbeddingServiceImpl(KbSpaceMapper kbSpaceMapper,
                                         KbDocumentMapper kbDocumentMapper,
                                         KbChunkMapper kbChunkMapper,
                                         VectorStore vectorStore,
                                         KeywordIndexService keywordIndexService,
                                         @Value("${spring.ai.vectorstore.qdrant.collection-name}") String collectionName,
                                         @Value("${rag.retrieval.vector-max-top-k:50}") int maxTopK) {
        this.kbSpaceMapper = kbSpaceMapper;
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbChunkMapper = kbChunkMapper;
        this.vectorStore = vectorStore;
        this.keywordIndexService = keywordIndexService;
        this.collectionName = collectionName;
        this.maxTopK = maxTopK >= MIN_TOP_K ? maxTopK : DEFAULT_MAX_TOP_K;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IndexBuildResult buildDocumentIndex(Long documentId, boolean force) {
        if (documentId == null) {
            throw new BusinessException("documentId cannot be null");
        }

        KbDocument document = kbDocumentMapper.selectById(documentId);
        if (document == null || DocumentStatus.INVALID.getValue() == document.getStatus()) {
            throw new BusinessException("document not found");
        }

        Long totalChunkCount = kbChunkMapper.selectCount(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getDocumentId, documentId)
                        .eq(KbChunk::getStatus, STATUS_NORMAL)
        );

        List<KbChunk> pendingChunks;
        if (force) {
            // 强制重建（REPAIR_INDEX）：忽略 embedding_status，全量重写全部正常分块
            pendingChunks = kbChunkMapper.selectList(
                    new LambdaQueryWrapper<KbChunk>()
                            .eq(KbChunk::getDocumentId, documentId)
                            .eq(KbChunk::getStatus, STATUS_NORMAL)
                            .orderByAsc(KbChunk::getChunkIndex)
            );
        } else {
            // 非 force（BUILD_INDEX）重试语义：pending（首次）与 failed（重试）一起处理；
            // 已成功的 chunk（embedding_status=1）保留不重复处理，其 Qdrant/ES 索引不动。
            pendingChunks = kbChunkMapper.selectList(
                    new LambdaQueryWrapper<KbChunk>()
                            .eq(KbChunk::getDocumentId, documentId)
                            .eq(KbChunk::getStatus, STATUS_NORMAL)
                            .in(KbChunk::getEmbeddingStatus, EMBEDDING_PENDING, EMBEDDING_FAILED)
                            .orderByAsc(KbChunk::getChunkIndex)
            );
        }

        // 重新进入索引构建：非 force 的 FAILED 重试，或 force 重建 ACTIVE/FAILED 文档，
        // 都先置回 INDEXING（构建期间不可检索）
        boolean shouldResetIndexing;
        if (force) {
            shouldResetIndexing = DocumentStatus.ACTIVE.getValue() == document.getStatus()
                    || DocumentStatus.FAILED.getValue() == document.getStatus();
        } else {
            shouldResetIndexing = DocumentStatus.FAILED.getValue() == document.getStatus();
        }
        if (shouldResetIndexing) {
            // 条件更新的源状态与目标状态(INDEXING)互斥，因此影响行数语义明确：
            // 0 行 ⇔ 当前状态既不是 ACTIVE 也不是 FAILED，即文档已被并发删除/更新（INVALID）
            // 或被其它流程接管。此时必须中止，否则会把已失效文档重新置为 INDEXING，
            // 进而在收尾时被写成 ACTIVE（即“复活”）。
            int reset = kbDocumentMapper.update(
                    null,
                    new LambdaUpdateWrapper<KbDocument>()
                            .eq(KbDocument::getId, documentId)
                            .in(KbDocument::getStatus,
                                    DocumentStatus.ACTIVE.getValue(), DocumentStatus.FAILED.getValue())
                            .set(KbDocument::getStatus, DocumentStatus.INDEXING.getValue())
            );
            if (reset == 0) {
                abortInvalidatedBuild(documentId);
            }
        }

        int successCount = 0;
        int failedCount = 0;

        for (KbChunk chunk : pendingChunks) {
            String vectorId = buildVectorId(chunk.getId());

            Map<String, Object> metadata = new HashMap<>(12);
            metadata.put("source", "kb_chunk");
            metadata.put("chunk_id", String.valueOf(chunk.getId()));
            metadata.put("document_id", String.valueOf(chunk.getDocumentId()));
            metadata.put("space_id", String.valueOf(chunk.getSpaceId()));
            metadata.put("chunk_index", chunk.getChunkIndex());

            Document vectorDocument = new Document(vectorId, chunk.getContent(), metadata);

            try {
                vectorStore.add(List.of(vectorDocument));
                keywordIndexService.upsertChunk(chunk);

                kbChunkMapper.update(
                        null,
                        new LambdaUpdateWrapper<KbChunk>()
                                .eq(KbChunk::getId, chunk.getId())
                                .set(KbChunk::getEmbeddingStatus, EMBEDDING_SUCCESS)
                                .set(KbChunk::getVectorId, vectorId)
                );

                successCount++;
            } catch (Exception e) {
                kbChunkMapper.update(
                        null,
                        new LambdaUpdateWrapper<KbChunk>()
                                .eq(KbChunk::getId, chunk.getId())
                                .set(KbChunk::getEmbeddingStatus, EMBEDDING_FAILED)
                );
                log.error("chunk 索引写入失败: chunkId={} documentId={}",
                        chunk.getId(), documentId, e);
                failedCount++;
            }
        }

        // 全 chunk 成功 → ACTIVE（生效）；存在失败 → FAILED（可重试，成功后部分保留）
        DocumentStatus finalStatus = failedCount == 0 ? DocumentStatus.ACTIVE : DocumentStatus.FAILED;

        // 收尾前以“锁定读”确认文档未失效。锁定读是当前读，能看到并发事务已提交的最新值
        // （普通 SELECT 在本方法的长事务里会命中 REPEATABLE READ 快照，读不到并发删除/更新）。
        // 读出后本事务持有该行共享锁至提交，并发删除/更新无法在提交前把状态改成 INVALID，
        // 因此紧随其后的写入不会与之交错。此处已接近方法末尾，持锁窗口极短。
        Integer observedStatus = kbDocumentMapper.selectStatusForShare(documentId);
        if (observedStatus == null || DocumentStatus.INVALID.getValue() == observedStatus) {
            abortInvalidatedBuild(documentId);
        }

        // 此处不再用影响行数判断：UPDATE 在“匹配但值未变化”时同样返回 0 行
        // （例如对 ACTIVE 文档手动重建且全部成功，终态仍是 ACTIVE），用 0 行判定失效会误删健康索引。
        kbDocumentMapper.update(
                null,
                new LambdaUpdateWrapper<KbDocument>()
                        .eq(KbDocument::getId, documentId)
                        .set(KbDocument::getStatus, finalStatus.getValue())
        );

        IndexBuildResult response = new IndexBuildResult();
        response.setDocumentId(documentId);
        response.setTotalChunkCount(totalChunkCount.intValue());
        response.setPendingChunkCount(pendingChunks.size());
        response.setSuccessCount(successCount);
        response.setFailedCount(failedCount);
        response.setSkippedCount(totalChunkCount.intValue() - pendingChunks.size());

        return response;
    }

    /**
     * 文档已失效（被删除或更新）时中止构建：清理本次写入的索引后抛出
     * {@link IndexBuildAbortedException}，由任务层直接置 FAILED 终态（不重试）。
     */
    private void abortInvalidatedBuild(Long documentId) {
        cleanupPartialIndexes(documentId);
        throw new IndexBuildAbortedException(
                "document invalidated during index build, build aborted: documentId=" + documentId);
    }

    /**
     * 清理某文档在 Qdrant 与 ES 中的索引。两步均为按 document_id 过滤的幂等操作。
     * <p>清理失败只告警：中止本身已是确定结果，遗留数据由周期对账兜底。</p>
     * <p>此处直接调用两个底层删除而非注入 {@code IndexCleanupService}——后者依赖本服务，
     * 反向注入会形成循环依赖。</p>
     */
    private void cleanupPartialIndexes(Long documentId) {
        try {
            deleteVectorsByDocumentId(documentId);
            keywordIndexService.deleteByDocumentId(documentId);
        } catch (Exception e) {
            log.warn("清理中止构建的索引失败，留待对账兜底: documentId={}, 错误={}", documentId, e.getMessage());
        }
    }

    @Override
    public List<SearchChunkResponse> searchChunks(SearchChunksRequest request) {
        Long spaceId = request.getSpaceId();

        KbSpace space = kbSpaceMapper.selectById(spaceId);
        if (space == null || !STATUS_NORMAL.equals(space.getStatus())) {
            throw new BusinessException("space not found");
        }

        int topK = normalizeTopK(request.getTopK());

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(request.getQuery())
                        .topK(Math.max(topK * 3, 10))
                        .filterExpression("space_id == '" + spaceId + "'")
                        .build()
        );

        List<SearchChunkResponse> resultList = new ArrayList<>();

        for (Document document : documents) {
            if (resultList.size() >= topK) {
                break;
            }

            Object chunkIdValue = document.getMetadata().get("chunk_id");
            if (chunkIdValue == null) {
                continue;
            }

            Long chunkId;
            try {
                chunkId = Long.valueOf(String.valueOf(chunkIdValue));
            } catch (NumberFormatException e) {
                continue;
            }

            KbChunk chunk = kbChunkMapper.selectById(chunkId);
            if (chunk == null || !STATUS_NORMAL.equals(chunk.getStatus())) {
                continue;
            }

            KbDocument kbDocument = kbDocumentMapper.selectById(chunk.getDocumentId());
            // 检索只认已生效（ACTIVE）文档：INDEXING/FAILED 未生效不返回
            if (kbDocument == null || DocumentStatus.ACTIVE.getValue() != kbDocument.getStatus()) {
                continue;
            }

            SearchChunkResponse response = new SearchChunkResponse();
            response.setChunkId(chunk.getId());
            response.setDocumentId(chunk.getDocumentId());
            response.setSpaceId(chunk.getSpaceId());
            response.setChunkIndex(chunk.getChunkIndex());
            response.setContent(chunk.getContent());
            response.setScore(document.getScore());

            resultList.add(response);
        }

        return resultList;
    }

    @Override
    public void deleteVectorsByDocumentId(Long documentId) {
        if (documentId == null) {
            throw new BusinessException("documentId cannot be null");
        }

        // metadata 中的 document_id 以字符串存储（见 buildDocumentIndex），filter 值必须用字符串匹配
        Expression filter = new Expression(
                ExpressionType.EQ,
                new Key("document_id"),
                new Filter.Value(String.valueOf(documentId))
        );

        vectorStore.delete(filter);
    }

    @Override
    public int reconcileOrphans(Set<Long> validChunkIds, Set<Long> existingChunkIds) {
        QdrantClient client = vectorStore.<QdrantClient>getNativeClient()
                .orElseThrow(() -> new BusinessException("无法获取 Qdrant 原生客户端，无法执行对账"));

        try {
            List<Points.PointId> orphanPointIds = new ArrayList<>();
            int scanned = 0;
            Points.PointId offset = null;

            do {
                ScrollPoints.Builder builder = ScrollPoints.newBuilder()
                        .setCollectionName(collectionName)
                        .setLimit(SCROLL_LIMIT)
                        .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                        .setWithVectors(WithVectorsSelector.newBuilder().setEnable(false).build());
                if (offset != null) {
                    builder.setOffset(offset);
                }

                ScrollResponse response = client.scrollAsync(builder.build())
                        .get(QDRANT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                for (RetrievedPoint point : response.getResultList()) {
                    scanned++;
                    Long chunkId = parseChunkIdFromPayload(point);
                    if (chunkId != null) {
                        existingChunkIds.add(chunkId);
                        // 有 chunk_id 但不在有效集合中的 point 视为孤儿；无 chunk_id 的不处理（避免误删非本应用数据）
                        if (!validChunkIds.contains(chunkId)) {
                            orphanPointIds.add(point.getId());
                        }
                    }
                }

                offset = response.hasNextPageOffset() ? response.getNextPageOffset() : null;
            } while (offset != null);

            if (!orphanPointIds.isEmpty()) {
                client.deleteAsync(collectionName, orphanPointIds)
                        .get(QDRANT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

            return orphanPointIds.size();
        } catch (Exception e) {
            throw new BusinessException("Qdrant 对账失败: " + e.getMessage());
        }
    }

    private Long parseChunkIdFromPayload(RetrievedPoint point) {
        try {
            String chunkIdValue = point.getPayloadMap().get("chunk_id").getStringValue();
            return chunkIdValue == null ? null : Long.valueOf(chunkIdValue);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildVectorId(Long chunkId) {
        return java.util.UUID.nameUUIDFromBytes(
                ("kb_chunk_" + chunkId).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        ).toString();
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        if (topK < MIN_TOP_K) {
            return MIN_TOP_K;
        }
        if (topK > maxTopK) {
            return maxTopK;
        }
        return topK;
    }
}
