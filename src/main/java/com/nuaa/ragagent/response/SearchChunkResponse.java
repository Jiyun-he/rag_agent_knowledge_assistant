package com.nuaa.ragagent.response;

public class SearchChunkResponse {

    private Long chunkId;

    private Long documentId;

    private Long spaceId;

    private Integer chunkIndex;

    private String content;

    private Double score;

    private Double vectorScore;

    private Double keywordScore;

    private Double hybridScore;

    private Double rerankScore;

    private Double finalScore;

    private String retrievalSource;

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getVectorScore() {
        return vectorScore;
    }

    public SearchChunkResponse setVectorScore(Double vectorScore) {
        this.vectorScore = vectorScore;
        return this;
    }

    public Double getKeywordScore() {
        return keywordScore;
    }

    public SearchChunkResponse setKeywordScore(Double keywordScore) {
        this.keywordScore = keywordScore;
        return this;
    }

    public Double getHybridScore() {
        return hybridScore;
    }

    public SearchChunkResponse setHybridScore(Double hybridScore) {
        this.hybridScore = hybridScore;
        return this;
    }

    public Double getRerankScore() {
        return rerankScore;
    }

    public SearchChunkResponse setRerankScore(Double rerankScore) {
        this.rerankScore = rerankScore;
        return this;
    }

    public Double getFinalScore() {
        return finalScore;
    }

    public SearchChunkResponse setFinalScore(Double finalScore) {
        this.finalScore = finalScore;
        return this;
    }

    public String getRetrievalSource() {
        return retrievalSource;
    }

    public SearchChunkResponse setRetrievalSource(String retrievalSource) {
        this.retrievalSource = retrievalSource;
        return this;
    }
}