package com.nuaa.ragagent.controller;

import com.nuaa.ragagent.common.ApiResponse;
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.service.RagRetrievalService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author jiyunhe
 */

@RestController
@RequestMapping("/api/rag")
public class RagRetrievalController {

    private final RagRetrievalService ragRetrievalService;

    public RagRetrievalController(RagRetrievalService ragRetrievalService) {
        this.ragRetrievalService = ragRetrievalService;
    }

    @PostMapping("/search")
    public ApiResponse<List<SearchChunkResponse>> search(@RequestBody SearchChunksRequest request) {
        return ApiResponse.success(ragRetrievalService.search(request));
    }
}
