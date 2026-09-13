package com.nuaa.ragagent.controller;

import com.nuaa.ragagent.common.ApiResponse;
import com.nuaa.ragagent.enums.TaskType;
import com.nuaa.ragagent.response.IndexTaskResponse;
import com.nuaa.ragagent.service.IndexTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * 持久化异步索引任务接口。
 *
 * @author jiyunhe
 */

@RestController
@RequestMapping("/api/rag")
public class IndexTaskController {

    private final IndexTaskService indexTaskService;

    public IndexTaskController(IndexTaskService indexTaskService) {
        this.indexTaskService = indexTaskService;
    }

    /**
     * 手动触发 BUILD_INDEX：登记任务（PENDING），由 worker 异步执行，接口立即返回任务详情。
     */
    @PostMapping("/documents/{documentId}/index")
    public ApiResponse<IndexTaskResponse> buildIndex(@PathVariable Long documentId) {
        return ApiResponse.success(indexTaskService.createTask(documentId, TaskType.BUILD_INDEX));
    }

    /**
     * 查询索引任务详情。
     */
    @GetMapping("/index-tasks/{taskId}")
    public ApiResponse<IndexTaskResponse> getIndexTask(@PathVariable Long taskId) {
        return ApiResponse.success(indexTaskService.getTask(taskId));
    }

    /**
     * 查询某文档的全部索引任务，按任务 ID 倒序。
     */
    @GetMapping("/documents/{documentId}/index-tasks")
    public ApiResponse<List<IndexTaskResponse>> listDocumentIndexTasks(@PathVariable Long documentId) {
        return ApiResponse.success(indexTaskService.listDocumentTasks(documentId));
    }

    /**
     * 人工重试 FAILED 任务：重置为 PENDING 重新入队执行。
     */
    @PostMapping("/index-tasks/{taskId}/retry")
    public ApiResponse<IndexTaskResponse> retryTask(@PathVariable Long taskId) {
        return ApiResponse.success(indexTaskService.retryTask(taskId));
    }
}
