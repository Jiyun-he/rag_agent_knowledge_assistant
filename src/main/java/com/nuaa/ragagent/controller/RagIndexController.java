package com.nuaa.ragagent.controller;

import com.nuaa.ragagent.common.ApiResponse;
import com.nuaa.ragagent.service.KeywordIndexService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * @author jiyunhe
 */
@RestController
@RequestMapping("/api/rag/index")
public class RagIndexController {

    private final KeywordIndexService keywordIndexService;

    public RagIndexController(KeywordIndexService keywordIndexService) {
        this.keywordIndexService = keywordIndexService;
    }

    /**
     * 全量重建 Elasticsearch 关键词索引（历史数据迁移用）。
     */
    @PostMapping("/rebuild")
    public ApiResponse<String> rebuild() {
        keywordIndexService.rebuildAll();
        return ApiResponse.success("索引重建完成");
    }
}
