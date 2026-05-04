package com.nuaa.ragagent.service;

import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.response.VectorizeDocumentResponse;

import java.util.List;

public interface KnowledgeEmbeddingService {

    VectorizeDocumentResponse vectorizeDocument(Long documentId);

    List<SearchChunkResponse> searchChunks(SearchChunksRequest request);
}