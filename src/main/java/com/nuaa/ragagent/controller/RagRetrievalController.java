package com.nuaa.ragagent.controller;

import com.nuaa.ragagent.common.ApiResponse;
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.response.VectorizeDocumentResponse;
import com.nuaa.ragagent.service.KnowledgeEmbeddingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class RagRetrievalController {

    private final KnowledgeEmbeddingService knowledgeEmbeddingService;

    public RagRetrievalController(KnowledgeEmbeddingService knowledgeEmbeddingService) {
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
    }

    @PostMapping("/search")
    public ApiResponse<List<SearchChunkResponse>> searchChunks(@Valid @RequestBody SearchChunksRequest request) {
        List<SearchChunkResponse> response = knowledgeEmbeddingService.searchChunks(request);
        return ApiResponse.success(response);
    }
}