package com.nuaa.ragagent.response;

public class RagMatchedChunkResponse {

    private Integer referenceIndex;

    private Long chunkId;

    private Long documentId;

    private Long spaceId;

    private Integer chunkIndex;

    private Double score;

    private String contentPreview;

    public Integer getReferenceIndex() {
        return referenceIndex;
    }

    public RagMatchedChunkResponse setReferenceIndex(Integer referenceIndex) {
        this.referenceIndex = referenceIndex;
        return this;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public RagMatchedChunkResponse setChunkId(Long chunkId) {
        this.chunkId = chunkId;
        return this;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public RagMatchedChunkResponse setDocumentId(Long documentId) {
        this.documentId = documentId;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public RagMatchedChunkResponse setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public RagMatchedChunkResponse setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
        return this;
    }

    public Double getScore() {
        return score;
    }

    public RagMatchedChunkResponse setScore(Double score) {
        this.score = score;
        return this;
    }

    public String getContentPreview() {
        return contentPreview;
    }

    public RagMatchedChunkResponse setContentPreview(String contentPreview) {
        this.contentPreview = contentPreview;
        return this;
    }
}