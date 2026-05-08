package com.nuaa.ragagent.controller;

import com.nuaa.ragagent.common.ApiResponse;
import com.nuaa.ragagent.response.EmbeddingTaskResponse;
import com.nuaa.ragagent.service.EmbeddingTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class EmbeddingTaskController {

    private final EmbeddingTaskService embeddingTaskService;

    public EmbeddingTaskController(EmbeddingTaskService embeddingTaskService) {
        this.embeddingTaskService = embeddingTaskService;
    }

    @PostMapping("/documents/{documentId}/vectorize-async")
    public ApiResponse<EmbeddingTaskResponse> vectorizeDocumentAsync(@PathVariable Long documentId) {
        EmbeddingTaskResponse task = embeddingTaskService.createTask(documentId);
        embeddingTaskService.runTaskAsync(task.getTaskId());
        return ApiResponse.success(task);
    }

    @GetMapping("/embedding-tasks/{taskId}")
    public ApiResponse<EmbeddingTaskResponse> getEmbeddingTask(@PathVariable Long taskId) {
        return ApiResponse.success(embeddingTaskService.getTask(taskId));
    }

    @GetMapping("/documents/{documentId}/embedding-tasks")
    public ApiResponse<List<EmbeddingTaskResponse>> listDocumentEmbeddingTasks(@PathVariable Long documentId) {
        return ApiResponse.success(embeddingTaskService.listDocumentTasks(documentId));
    }
}