package com.nuaa.ragagent.service;

import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.request.CreateDocumentRequest;
import com.nuaa.ragagent.request.UpdateDocumentRequest;

import java.util.List;

public interface KnowledgeDocumentService {

    KbDocument create(CreateDocumentRequest request);

    List<KbDocument> list(Long spaceId);

    KbDocument getById(Long id);

    KbDocument update(Long id, UpdateDocumentRequest request);

    Boolean delete(Long id);

    List<KbChunk> listChunks(Long documentId);
}