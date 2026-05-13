package com.nuaa.ragagent.request;

public class StartEvalRunRequest {

    private Long datasetId;

    private String runName;

    private String retrievalMode;

    private Integer topK;

    private Integer candidateK;

    private Double vectorWeight;

    private Double keywordWeight;

    private Boolean enableAnswerGeneration;

    public Long getDatasetId() {
        return datasetId;
    }

    public StartEvalRunRequest setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
        return this;
    }

    public String getRunName() {
        return runName;
    }

    public StartEvalRunRequest setRunName(String runName) {
        this.runName = runName;
        return this;
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public StartEvalRunRequest setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
        return this;
    }

    public Integer getTopK() {
        return topK;
    }

    public StartEvalRunRequest setTopK(Integer topK) {
        this.topK = topK;
        return this;
    }

    public Integer getCandidateK() {
        return candidateK;
    }

    public StartEvalRunRequest setCandidateK(Integer candidateK) {
        this.candidateK = candidateK;
        return this;
    }

    public Double getVectorWeight() {
        return vectorWeight;
    }

    public StartEvalRunRequest setVectorWeight(Double vectorWeight) {
        this.vectorWeight = vectorWeight;
        return this;
    }

    public Double getKeywordWeight() {
        return keywordWeight;
    }

    public StartEvalRunRequest setKeywordWeight(Double keywordWeight) {
        this.keywordWeight = keywordWeight;
        return this;
    }

    public Boolean getEnableAnswerGeneration() {
        return enableAnswerGeneration;
    }

    public StartEvalRunRequest setEnableAnswerGeneration(Boolean enableAnswerGeneration) {
        this.enableAnswerGeneration = enableAnswerGeneration;
        return this;
    }
}