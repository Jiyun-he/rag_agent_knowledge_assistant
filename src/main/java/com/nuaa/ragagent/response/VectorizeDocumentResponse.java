package com.nuaa.ragagent.response;

public class VectorizeDocumentResponse {

    private Long documentId;

    private Integer totalChunkCount;

    private Integer pendingChunkCount;

    private Integer successCount;

    private Integer failedCount;

    private Integer skippedCount;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getTotalChunkCount() {
        return totalChunkCount;
    }

    public void setTotalChunkCount(Integer totalChunkCount) {
        this.totalChunkCount = totalChunkCount;
    }

    public Integer getPendingChunkCount() {
        return pendingChunkCount;
    }

    public void setPendingChunkCount(Integer pendingChunkCount) {
        this.pendingChunkCount = pendingChunkCount;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public Integer getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(Integer skippedCount) {
        this.skippedCount = skippedCount;
    }
}