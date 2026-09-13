package com.nuaa.ragagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
/**
 * 注意：kb_index_task 还有两个由 MySQL 维护的生成列 {@code dedup_group} 与 {@code active_flag}
 * （配合唯一索引 uk_task_active_dedup 做并发去重）。它们**刻意不映射为实体字段**——
 * MyBatis-Plus 若把它们纳入 INSERT/UPDATE 语句，MySQL 会报错 3105（生成列不可显式赋值）。
 *
 * @author jiyunhe
 */

@TableName("kb_index_task")
public class KbIndexTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long spaceId;

    /** 任务类型：BUILD_INDEX / DELETE_INDEX / REPAIR_INDEX */
    private String taskType;

    /** 任务状态：见 {@link com.nuaa.ragagent.enums.TaskStatus} */
    private Integer status;

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

    public Long getId() {
        return id;
    }

    public KbIndexTask setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public KbIndexTask setDocumentId(Long documentId) {
        this.documentId = documentId;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public KbIndexTask setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getTaskType() {
        return taskType;
    }

    public KbIndexTask setTaskType(String taskType) {
        this.taskType = taskType;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public KbIndexTask setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public KbIndexTask setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public KbIndexTask setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
        return this;
    }

    public Integer getTotalChunkCount() {
        return totalChunkCount;
    }

    public KbIndexTask setTotalChunkCount(Integer totalChunkCount) {
        this.totalChunkCount = totalChunkCount;
        return this;
    }

    public Integer getPendingChunkCount() {
        return pendingChunkCount;
    }

    public KbIndexTask setPendingChunkCount(Integer pendingChunkCount) {
        this.pendingChunkCount = pendingChunkCount;
        return this;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public KbIndexTask setSuccessCount(Integer successCount) {
        this.successCount = successCount;
        return this;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public KbIndexTask setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
        return this;
    }

    public Integer getSkippedCount() {
        return skippedCount;
    }

    public KbIndexTask setSkippedCount(Integer skippedCount) {
        this.skippedCount = skippedCount;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public KbIndexTask setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public KbIndexTask setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public KbIndexTask setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public KbIndexTask setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public KbIndexTask setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}
