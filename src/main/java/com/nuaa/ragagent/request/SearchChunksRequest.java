package com.nuaa.ragagent.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SearchChunksRequest {

    @NotNull(message = "spaceId cannot be null")
    private Long spaceId;

    @NotBlank(message = "query cannot be blank")
    @Size(max = 1000, message = "query length cannot exceed 1000")
    private String query;

    private String retrievalMode;

    private Integer topK=5;

    private Integer candidateK;

    private Double vectorWeight;

    private Double keywordWeight;

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Integer getCandidateK() {
        return candidateK;
    }

    public SearchChunksRequest setCandidateK(Integer candidateK) {
        this.candidateK = candidateK;
        return this;
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public SearchChunksRequest setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
        return this;
    }

    public Double getVectorWeight() {
        return vectorWeight;
    }

    public SearchChunksRequest setVectorWeight(Double vectorWeight) {
        this.vectorWeight = vectorWeight;
        return this;
    }

    public Double getKeywordWeight() {
        return keywordWeight;
    }

    public SearchChunksRequest setKeywordWeight(Double keywordWeight) {
        this.keywordWeight = keywordWeight;
        return this;
    }
}