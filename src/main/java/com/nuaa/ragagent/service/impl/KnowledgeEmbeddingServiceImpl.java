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
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.response.VectorizeDocumentResponse;
import com.nuaa.ragagent.service.KnowledgeEmbeddingService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeEmbeddingServiceImpl implements KnowledgeEmbeddingService {

    private static final Integer STATUS_NORMAL = 1;

    private static final Integer EMBEDDING_PENDING = 0;

    private static final Integer EMBEDDING_SUCCESS = 1;

    private static final Integer EMBEDDING_FAILED = 2;

    private final KbSpaceMapper kbSpaceMapper;

    private final KbDocumentMapper kbDocumentMapper;

    private final KbChunkMapper kbChunkMapper;

    private final VectorStore vectorStore;

    public KnowledgeEmbeddingServiceImpl(KbSpaceMapper kbSpaceMapper,
                                         KbDocumentMapper kbDocumentMapper,
                                         KbChunkMapper kbChunkMapper,
                                         VectorStore vectorStore) {
        this.kbSpaceMapper = kbSpaceMapper;
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbChunkMapper = kbChunkMapper;
        this.vectorStore = vectorStore;
    }

    @Override
    @Transactional
    public VectorizeDocumentResponse vectorizeDocument(Long documentId) {
        if (documentId == null) {
            throw new BusinessException("documentId cannot be null");
        }

        KbDocument document = kbDocumentMapper.selectById(documentId);
        if (document == null || !STATUS_NORMAL.equals(document.getStatus())) {
            throw new BusinessException("document not found");
        }

        Long totalChunkCount = kbChunkMapper.selectCount(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getDocumentId, documentId)
                        .eq(KbChunk::getStatus, STATUS_NORMAL)
        );

        List<KbChunk> pendingChunks = kbChunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getDocumentId, documentId)
                        .eq(KbChunk::getStatus, STATUS_NORMAL)
                        .eq(KbChunk::getEmbeddingStatus, EMBEDDING_PENDING)
                        .orderByAsc(KbChunk::getChunkIndex)
        );

        int successCount = 0;
        int failedCount = 0;

        for (KbChunk chunk : pendingChunks) {
            String vectorId = buildVectorId(chunk.getId());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "kb_chunk");
            metadata.put("chunk_id", String.valueOf(chunk.getId()));
            metadata.put("document_id", String.valueOf(chunk.getDocumentId()));
            metadata.put("space_id", String.valueOf(chunk.getSpaceId()));
            metadata.put("chunk_index", chunk.getChunkIndex());

            Document vectorDocument = new Document(vectorId, chunk.getContent(), metadata);

            try {
                vectorStore.add(List.of(vectorDocument));

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

                failedCount++;
            }
        }

        VectorizeDocumentResponse response = new VectorizeDocumentResponse();
        response.setDocumentId(documentId);
        response.setTotalChunkCount(totalChunkCount.intValue());
        response.setPendingChunkCount(pendingChunks.size());
        response.setSuccessCount(successCount);
        response.setFailedCount(failedCount);
        response.setSkippedCount(totalChunkCount.intValue() - pendingChunks.size());

        return response;
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
            if (kbDocument == null || !STATUS_NORMAL.equals(kbDocument.getStatus())) {
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

    private String buildVectorId(Long chunkId) {
        return "chunk-" + chunkId;
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return 5;
        }
        if (topK < 1) {
            return 1;
        }
        if (topK > 20) {
            return 20;
        }
        return topK;
    }
}