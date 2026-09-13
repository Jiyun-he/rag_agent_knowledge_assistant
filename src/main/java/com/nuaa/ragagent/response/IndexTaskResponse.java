package com.nuaa.ragagent.response;

import java.time.LocalDateTime;
/**
 * @author jiyunhe
 */

public class IndexTaskResponse {

    private Long taskId;

    private Long documentId;

    private Long spaceId;

    /** 任务类型：BUILD_INDEX / DELETE_INDEX / REPAIR_INDEX */
    private String taskType;

    /** 任务状态数值，见 TaskStatus */
    private Integer status;

    private String statusText;

    /** 已失败次数（含超时回收），重试计数 */
    private Integer retryCount;

    /** RETRY_WAIT 状态下的下次执行时间 */
    private LocalDateTime nextRetryAt;

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

    public IndexTaskResponse setTaskId(Long taskId) {
        this.taskId = taskId;
        return this;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public IndexTaskResponse setDocumentId(Long documentId) {
        this.documentId = documentId;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public IndexTaskResponse setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getTaskType() {
        return taskType;
    }

    public IndexTaskResponse setTaskType(String taskType) {
        this.taskType = taskType;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public IndexTaskResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public String getStatusText() {
        return statusText;
    }

    public IndexTaskResponse setStatusText(String statusText) {
        this.statusText = statusText;
        return this;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public IndexTaskResponse setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public IndexTaskResponse setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
        return this;
    }

    public Integer getTotalChunkCount() {
        return totalChunkCount;
    }

    public IndexTaskResponse setTotalChunkCount(Integer totalChunkCount) {
        this.totalChunkCount = totalChunkCount;
        return this;
    }

    public Integer getPendingChunkCount() {
        return pendingChunkCount;
    }

    public IndexTaskResponse setPendingChunkCount(Integer pendingChunkCount) {
        this.pendingChunkCount = pendingChunkCount;
        return this;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public IndexTaskResponse setSuccessCount(Integer successCount) {
        this.successCount = successCount;
        return this;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public IndexTaskResponse setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
        return this;
    }

    public Integer getSkippedCount() {
        return skippedCount;
    }

    public IndexTaskResponse setSkippedCount(Integer skippedCount) {
        this.skippedCount = skippedCount;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public IndexTaskResponse setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public IndexTaskResponse setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public IndexTaskResponse setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public IndexTaskResponse setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public IndexTaskResponse setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}
