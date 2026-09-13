package com.nuaa.ragagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.entity.KbSpace;
import com.nuaa.ragagent.enums.DocumentStatus;
import com.nuaa.ragagent.enums.TaskType;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.mapper.KbChunkMapper;
import com.nuaa.ragagent.mapper.KbDocumentMapper;
import com.nuaa.ragagent.mapper.KbSpaceMapper;
import com.nuaa.ragagent.request.CreateDocumentRequest;
import com.nuaa.ragagent.request.UpdateDocumentRequest;
import com.nuaa.ragagent.service.IndexTaskService;
import com.nuaa.ragagent.service.KnowledgeDocumentService;
import com.nuaa.ragagent.util.Chunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
/**
 * 文档生命周期管理。
 *
 * <p>文档四态：INDEXING（新建/索引构建中）→ ACTIVE（全部 chunk 索引构建成功，生效）/
 * FAILED（索引构建存在失败，可重试）；删除或更新后旧文档 → INVALID（永不物理删除）。</p>
 *
 * <p>删除/更新均不物理删除文档记录：删除 = 文档置 INVALID + chunk 物理清理 + 提交后登记
 * DELETE_INDEX 任务；更新 = 旧文档置 INVALID + 旧 chunk 清理 + 复用"新增"流程建新文档
 * （INDEXING）+ 提交后登记 DELETE_INDEX(旧文档) 与 BUILD_INDEX(新文档) 任务。
 * 索引工作全部由持久化任务 {@link IndexTaskService} 异步执行，核心原则是先关闭业务可见性，
 * 再通过任务队列做物理清理/构建，失败交由任务重试与对账兜底。</p>
 *
 * @author jiyunhe
 */

@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentServiceImpl.class);

    /** 空间/分块的活跃状态（非文档状态机，共用 status=1 语义） */
    private static final int STATUS_NORMAL = 1;

    private final KbDocumentMapper kbDocumentMapper;

    private final KbSpaceMapper kbSpaceMapper;

    private final KbChunkMapper kbChunkMapper;

    private final Chunker chunker;

    private final IndexTaskService indexTaskService;

    public KnowledgeDocumentServiceImpl(KbDocumentMapper kbDocumentMapper,
                                        KbSpaceMapper kbSpaceMapper,
                                        KbChunkMapper kbChunkMapper,
                                        Chunker chunker,
                                        IndexTaskService indexTaskService) {
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbSpaceMapper = kbSpaceMapper;
        this.kbChunkMapper = kbChunkMapper;
        this.chunker = chunker;
        this.indexTaskService = indexTaskService;
    }

    /**
     * 新增文档：插入 INDEXING 状态文档 + 重建 chunk，事务提交后登记 BUILD_INDEX 任务，
     * 由任务执行驱动 INDEXING → ACTIVE/FAILED。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbDocument create(CreateDocumentRequest request) {
        checkSpaceExists(request.getSpaceId());

        String sourceType = request.getSourceType();
        if (sourceType == null || sourceType.trim().isEmpty()) {
            sourceType = "MANUAL";
        }

        return buildNewDocument(
                request.getSpaceId(),
                request.getTitle(),
                request.getContent(),
                sourceType,
                request.getSourceUri(),
                null
        );
    }

    @Override
    public List<KbDocument> list(Long spaceId) {
        LambdaQueryWrapper<KbDocument> queryWrapper = new LambdaQueryWrapper<KbDocument>()
                .ne(KbDocument::getStatus, DocumentStatus.INVALID.getValue())
                .orderByDesc(KbDocument::getId);

        if (spaceId != null) {
            queryWrapper.eq(KbDocument::getSpaceId, spaceId);
        }

        return kbDocumentMapper.selectList(queryWrapper);
    }

    /**
     * 按 ID 获取非 INVALID 文档（ACTIVE/INDEXING/FAILED 均可获取，用于操作与展示）。
     */
    @Override
    public KbDocument getById(Long id) {
        KbDocument document = kbDocumentMapper.selectOne(
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getId, id)
                        .ne(KbDocument::getStatus, DocumentStatus.INVALID.getValue())
        );

        if (document == null) {
            throw new BusinessException("Document not found");
        }

        return document;
    }

    /**
     * 更新文档：旧文档置 INVALID + 物理清理旧 chunk，然后复用"新增"流程建新文档（INDEXING）。
     * <p>事务提交后登记 DELETE_INDEX(旧文档) 与 BUILD_INDEX(新文档) 任务，由 worker 异步执行；
     * 新文档索引构建完成前保持 INDEXING 不可检索（短暂不可检索窗口）。返回新文档（id 与路径 id 不同）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbDocument update(Long id, UpdateDocumentRequest request) {
        KbDocument existingDocument = getById(id);

        Long targetSpaceId = existingDocument.getSpaceId();
        if (request.getSpaceId() != null) {
            checkSpaceExists(request.getSpaceId());
            targetSpaceId = request.getSpaceId();
        }

        String sourceType = request.getSourceType();
        if (sourceType == null || sourceType.trim().isEmpty()) {
            sourceType = existingDocument.getSourceType();
        }

        // TODO(known-issue): sourceUri 未像 sourceType 一样回退旧值 —— 更新请求不传 sourceUri 时，
        //  新文档的 sourceUri 会被置为 null，而旧文档已置 INVALID 无法回查，来源信息就此丢失。
        //  修复方向：与 sourceType 一致，为空时回退 existingDocument.getSourceUri()。

        // 1. 旧文档失效：先关闭业务可见性
        invalidateDocument(id);

        // 2. 物理删除旧 chunk（MySQL）
        kbChunkMapper.delete(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getDocumentId, id)
        );

        // 3. 复用"新增"流程：建 INDEXING 新文档 + 提交后清旧索引 + 自动向量化
        return buildNewDocument(
                targetSpaceId,
                request.getTitle(),
                request.getContent(),
                sourceType,
                request.getSourceUri(),
                id
        );
    }

    /**
     * 删除文档：文档置 INVALID，物理清理 chunk，事务提交后清理 Qdrant/ES 索引。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean delete(Long id) {
        getById(id);

        // 1. 文档失效：先关闭业务可见性
        invalidateDocument(id);

        // 2. 物理删除 chunk（MySQL）
        kbChunkMapper.delete(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getDocumentId, id)
        );

        // 3. 事务提交后登记 DELETE_INDEX 任务清理 Qdrant/ES 索引
        registerIndexTaskAfterCommit(id, null);

        return true;
    }

    @Override
    public List<KbChunk> listChunks(Long documentId) {
        getById(documentId);

        return kbChunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getDocumentId, documentId)
                        .eq(KbChunk::getStatus, STATUS_NORMAL)
                        .orderByAsc(KbChunk::getChunkIndex)
        );
    }

    /**
     * 新建文档的公共流程（create/update 共用）：
     * 插入 INDEXING 状态文档 + 重建 chunk，事务提交后由调用方上下文决定后续动作
     * （清旧索引 + 自动向量化新文档）。
     *
     * @param invalidOldDocumentId 被替换的旧文档 ID（新增时传 null；更新时传旧 id，提交后清其索引）
     * @return 新文档（INDEXING 状态）
     */
    private KbDocument buildNewDocument(Long spaceId, String title, String content,
                                        String sourceType, String sourceUri, Long invalidOldDocumentId) {
        KbDocument document = new KbDocument()
                .setSpaceId(spaceId)
                .setTitle(title)
                .setContent(content)
                .setSourceType(sourceType)
                .setSourceUri(sourceUri)
                .setStatus(DocumentStatus.INDEXING.getValue())
                .setChunkCount(0);

        kbDocumentMapper.insert(document);

        int chunkCount = rebuildChunks(document);
        document.setChunkCount(chunkCount);
        kbDocumentMapper.updateById(document);

        registerIndexTaskAfterCommit(invalidOldDocumentId, document.getId());

        return getById(document.getId());
    }

    private void invalidateDocument(Long id) {
        kbDocumentMapper.update(
                null,
                new LambdaUpdateWrapper<KbDocument>()
                        .eq(KbDocument::getId, id)
                        .set(KbDocument::getStatus, DocumentStatus.INVALID.getValue())
        );
    }

    private void checkSpaceExists(Long spaceId) {
        KbSpace space = kbSpaceMapper.selectOne(
                new LambdaQueryWrapper<KbSpace>()
                        .eq(KbSpace::getId, spaceId)
                        .eq(KbSpace::getStatus, STATUS_NORMAL)
        );

        if (space == null) {
            throw new BusinessException("Space not found");
        }
    }

    /**
     * 事务提交后登记持久化索引任务（不直接执行索引工作，执行交给 worker 调度）。
     * 登记失败不抛错，避免影响主流程（可人工触发或对账兜底）。
     */
    private void registerIndexTaskAfterCommit(Long deleteDocumentId, Long buildDocumentId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    if (deleteDocumentId != null) {
                        indexTaskService.createTask(deleteDocumentId, TaskType.DELETE_INDEX);
                    }
                    if (buildDocumentId != null) {
                        indexTaskService.createTask(buildDocumentId, TaskType.BUILD_INDEX);
                    }
                } catch (Exception e) {
                    log.warn("提交后登记索引任务失败: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * 将全文切分并插入 chunk（embedding_status=0 待向量化）。不负责索引清理，清理由调用方负责。
     */
    private int rebuildChunks(KbDocument document) {
        List<String> chunks = chunker.split(document.getContent());

        for (int i = 0; i < chunks.size(); i++) {
            String chunkContent = chunks.get(i);

            KbChunk chunk = new KbChunk()
                    .setDocumentId(document.getId())
                    .setSpaceId(document.getSpaceId())
                    .setChunkIndex(i)
                    .setContent(chunkContent)
                    .setCharCount(chunkContent.length())
                    .setEmbeddingStatus(0)
                    .setVectorId(null)
                    .setStatus(STATUS_NORMAL);

            kbChunkMapper.insert(chunk);
        }

        return chunks.size();
    }
}
