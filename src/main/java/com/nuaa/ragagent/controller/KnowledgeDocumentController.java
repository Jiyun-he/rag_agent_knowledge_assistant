package com.nuaa.ragagent.controller;

import com.nuaa.ragagent.common.ApiResponse;
import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.request.CreateDocumentRequest;
import com.nuaa.ragagent.request.UpdateDocumentRequest;
import com.nuaa.ragagent.service.KnowledgeDocumentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    public KnowledgeDocumentController(KnowledgeDocumentService knowledgeDocumentService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    @PostMapping
    public ApiResponse<KbDocument> create(@Valid @RequestBody CreateDocumentRequest request) {
        return ApiResponse.success(knowledgeDocumentService.create(request));
    }

    @GetMapping
    public ApiResponse<List<KbDocument>> list(@RequestParam(required = false) Long spaceId) {
        return ApiResponse.success(knowledgeDocumentService.list(spaceId));
    }

    @GetMapping("/{id}")
    public ApiResponse<KbDocument> getById(@PathVariable Long id) {
        return ApiResponse.success(knowledgeDocumentService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<KbDocument> update(@PathVariable Long id,
                                          @Valid @RequestBody UpdateDocumentRequest request) {
        return ApiResponse.success(knowledgeDocumentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        return ApiResponse.success(knowledgeDocumentService.delete(id));
    }

    @GetMapping("/{id}/chunks")
    public ApiResponse<List<KbChunk>> listChunks(@PathVariable Long id) {
        return ApiResponse.success(knowledgeDocumentService.listChunks(id));
    }
}