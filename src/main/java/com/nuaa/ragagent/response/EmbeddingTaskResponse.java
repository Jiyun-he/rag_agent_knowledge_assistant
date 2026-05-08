package com.nuaa.ragagent.response;

import java.time.LocalDateTime;

public class EmbeddingTaskResponse {

    private Long taskId;

    private Long documentId;

    private Long spaceId;

    private String taskType;

    private Integer status;

    private String statusText;

    private Integer totalChunkCount;

    private Integer pendingChunkCount;

    private Integer successCount;

    private Integer failedCount;

    private Integer skippedCount;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getTaskId() {
        return taskId;
    }

    public EmbeddingTaskResponse setTaskId(Long taskId) {
        this.taskId = taskId;
        return this;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public EmbeddingTaskResponse setDocumentId(Long documentId) {
        this.documentId = documentId;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public EmbeddingTaskResponse setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getTaskType() {
        return taskType;
    }

    public EmbeddingTaskResponse setTaskType(String taskType) {
        this.taskType = taskType;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public EmbeddingTaskResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public String getStatusText() {
        return statusText;
    }

    public EmbeddingTaskResponse setStatusText(String statusText) {
        this.statusText = statusText;
        return this;
    }

    public Integer getTotalChunkCount() {
        return totalChunkCount;
    }

    public EmbeddingTaskResponse setTotalChunkCount(Integer totalChunkCount) {
        this.totalChunkCount = totalChunkCount;
        return this;
    }

    public Integer getPendingChunkCount() {
        return pendingChunkCount;
    }

    public EmbeddingTaskResponse setPendingChunkCount(Integer pendingChunkCount) {
        this.pendingChunkCount = pendingChunkCount;
        return this;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public EmbeddingTaskResponse setSuccessCount(Integer successCount) {
        this.successCount = successCount;
        return this;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public EmbeddingTaskResponse setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
        return this;
    }

    public Integer getSkippedCount() {
        return skippedCount;
    }

    public EmbeddingTaskResponse setSkippedCount(Integer skippedCount) {
        this.skippedCount = skippedCount;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public EmbeddingTaskResponse setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public EmbeddingTaskResponse setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public EmbeddingTaskResponse setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public EmbeddingTaskResponse setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public EmbeddingTaskResponse setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}