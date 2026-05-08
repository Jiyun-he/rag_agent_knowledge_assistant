package com.nuaa.ragagent.service;

import com.nuaa.ragagent.response.EmbeddingTaskResponse;

import java.util.List;

public interface EmbeddingTaskService {

    EmbeddingTaskResponse createTask(Long documentId);

    void runTaskAsync(Long taskId);

    EmbeddingTaskResponse getTask(Long taskId);

    List<EmbeddingTaskResponse> listDocumentTasks(Long documentId);
}