package com.nuaa.ragagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("kb_embedding_task")
public class KbEmbeddingTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long spaceId;

    private String taskType;

    private Integer status;

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

    public Long getId() {
        return id;
    }

    public KbEmbeddingTask setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public KbEmbeddingTask setDocumentId(Long documentId) {
        this.documentId = documentId;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public KbEmbeddingTask setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getTaskType() {
        return taskType;
    }

    public KbEmbeddingTask setTaskType(String taskType) {
        this.taskType = taskType;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public KbEmbeddingTask setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Integer getTotalChunkCount() {
        return totalChunkCount;
    }

    public KbEmbeddingTask setTotalChunkCount(Integer totalChunkCount) {
        this.totalChunkCount = totalChunkCount;
        return this;
    }

    public Integer getPendingChunkCount() {
        return pendingChunkCount;
    }

    public KbEmbeddingTask setPendingChunkCount(Integer pendingChunkCount) {
        this.pendingChunkCount = pendingChunkCount;
        return this;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public KbEmbeddingTask setSuccessCount(Integer successCount) {
        this.successCount = successCount;
        return this;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public KbEmbeddingTask setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
        return this;
    }

    public Integer getSkippedCount() {
        return skippedCount;
    }

    public KbEmbeddingTask setSkippedCount(Integer skippedCount) {
        this.skippedCount = skippedCount;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public KbEmbeddingTask setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public KbEmbeddingTask setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public KbEmbeddingTask setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public KbEmbeddingTask setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public KbEmbeddingTask setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}