package com.nuaa.ragagent.controller;

import com.nuaa.ragagent.common.ApiResponse;
import com.nuaa.ragagent.request.AskRequest;
import com.nuaa.ragagent.response.AskResponse;
import com.nuaa.ragagent.service.RagQaService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author jiyunhe
 */

@RestController
@RequestMapping("/api/rag")
public class RagQaController {

    private final RagQaService ragQaService;

    public RagQaController(RagQaService ragQaService) {
        this.ragQaService = ragQaService;
    }

    @PostMapping("/ask")
    public ApiResponse<AskResponse> ask(@RequestBody AskRequest request) {
        return ApiResponse.success(ragQaService.ask(request));
    }
}
