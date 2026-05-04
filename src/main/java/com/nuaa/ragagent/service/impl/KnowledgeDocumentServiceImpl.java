package com.nuaa.ragagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.entity.KbSpace;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.mapper.KbChunkMapper;
import com.nuaa.ragagent.mapper.KbDocumentMapper;
import com.nuaa.ragagent.mapper.KbSpaceMapper;
import com.nuaa.ragagent.request.CreateDocumentRequest;
import com.nuaa.ragagent.request.UpdateDocumentRequest;
import com.nuaa.ragagent.service.KnowledgeDocumentService;
import com.nuaa.ragagent.util.TextChunker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private final KbDocumentMapper kbDocumentMapper;

    private final KbSpaceMapper kbSpaceMapper;

    private final KbChunkMapper kbChunkMapper;

    private final TextChunker textChunker;

    public KnowledgeDocumentServiceImpl(KbDocumentMapper kbDocumentMapper,
                                        KbSpaceMapper kbSpaceMapper,
                                        KbChunkMapper kbChunkMapper,
                                        TextChunker textChunker) {
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbSpaceMapper = kbSpaceMapper;
        this.kbChunkMapper = kbChunkMapper;
        this.textChunker = textChunker;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbDocument create(CreateDocumentRequest request) {
        checkSpaceExists(request.getSpaceId());

        String sourceType = request.getSourceType();
        if (sourceType == null || sourceType.trim().isEmpty()) {
            sourceType = "MANUAL";
        }

        KbDocument document = new KbDocument()
                .setSpaceId(request.getSpaceId())
                .setTitle(request.getTitle())
                .setContent(request.getContent())
                .setSourceType(sourceType)
                .setSourceUri(request.getSourceUri())
                .setStatus(1)
                .setChunkCount(0);

        kbDocumentMapper.insert(document);

        int chunkCount = rebuildChunks(document);

        document.setChunkCount(chunkCount);
        kbDocumentMapper.updateById(document);

        return getById(document.getId());
    }

    @Override
    public List<KbDocument> list(Long spaceId) {
        LambdaQueryWrapper<KbDocument> queryWrapper = new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getStatus, 1)
                .orderByDesc(KbDocument::getId);

        if (spaceId != null) {
            queryWrapper.eq(KbDocument::getSpaceId, spaceId);
        }

        return kbDocumentMapper.selectList(queryWrapper);
    }

    @Override
    public KbDocument getById(Long id) {
        KbDocument document = kbDocumentMapper.selectOne(
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getId, id)
                        .eq(KbDocument::getStatus, 1)
        );

        if (document == null) {
            throw new BusinessException("Document not found");
        }

        return document;
    }

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

        existingDocument
                .setSpaceId(targetSpaceId)
                .setTitle(request.getTitle())
                .setContent(request.getContent())
                .setSourceType(sourceType)
                .setSourceUri(request.getSourceUri());

        kbDocumentMapper.updateById(existingDocument);

        int chunkCount = rebuildChunks(existingDocument);

        existingDocument.setChunkCount(chunkCount);
        kbDocumentMapper.updateById(existingDocument);

        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean delete(Long id) {
        getById(id);

        kbDocumentMapper.update(
                null,
                new LambdaUpdateWrapper<KbDocument>()
                        .eq(KbDocument::getId, id)
                        .set(KbDocument::getStatus, 0)
        );

        kbChunkMapper.update(
                null,
                new LambdaUpdateWrapper<KbChunk>()
                        .eq(KbChunk::getDocumentId, id)
                        .set(KbChunk::getStatus, 0)
        );

        return true;
    }

    @Override
    public List<KbChunk> listChunks(Long documentId) {
        getById(documentId);

        return kbChunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getDocumentId, documentId)
                        .eq(KbChunk::getStatus, 1)
                        .orderByAsc(KbChunk::getChunkIndex)
        );
    }

    private void checkSpaceExists(Long spaceId) {
        KbSpace space = kbSpaceMapper.selectOne(
                new LambdaQueryWrapper<KbSpace>()
                        .eq(KbSpace::getId, spaceId)
                        .eq(KbSpace::getStatus, 1)
        );

        if (space == null) {
            throw new BusinessException("Space not found");
        }
    }

    private int rebuildChunks(KbDocument document) {
        kbChunkMapper.delete(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getDocumentId, document.getId())
        );

        List<String> chunks = textChunker.split(document.getContent());

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
                    .setStatus(1);

            kbChunkMapper.insert(chunk);
        }

        return chunks.size();
    }
}