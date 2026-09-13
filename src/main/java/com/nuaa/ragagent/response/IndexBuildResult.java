package com.nuaa.ragagent.response;
/**
 * 文档索引构建结果（BUILD_INDEX / REPAIR_INDEX 任务执行体返回）。
 *
 * @author jiyunhe
 */

public class IndexBuildResult {

    private Long documentId;

    /** 文档全部正常 chunk 数 */
    private Integer totalChunkCount;

    /** 本次实际处理的 chunk 数（非 force 模式下不含已成功的） */
    private Integer pendingChunkCount;

    private Integer successCount;

    private Integer failedCount;

    /** 未重新处理的 chunk 数（非 force 模式下 = 已成功的存量） */
    private Integer skippedCount;

    public Long getDocumentId() {
        return documentId;
    }

    public IndexBuildResult setDocumentId(Long documentId) {
        this.documentId = documentId;
        return this;
    }

    public Integer getTotalChunkCount() {
        return totalChunkCount;
    }

    public IndexBuildResult setTotalChunkCount(Integer totalChunkCount) {
        this.totalChunkCount = totalChunkCount;
        return this;
    }

    public Integer getPendingChunkCount() {
        return pendingChunkCount;
    }

    public IndexBuildResult setPendingChunkCount(Integer pendingChunkCount) {
        this.pendingChunkCount = pendingChunkCount;
        return this;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public IndexBuildResult setSuccessCount(Integer successCount) {
        this.successCount = successCount;
        return this;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public IndexBuildResult setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
        return this;
    }

    public Integer getSkippedCount() {
        return skippedCount;
    }

    public IndexBuildResult setSkippedCount(Integer skippedCount) {
        this.skippedCount = skippedCount;
        return this;
    }
}
