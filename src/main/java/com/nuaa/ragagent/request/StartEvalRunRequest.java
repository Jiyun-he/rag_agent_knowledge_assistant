package com.nuaa.ragagent.request;
/**
 * @author jiyunhe
 */

public class StartEvalRunRequest {

    private Long datasetId;

    private String runName;

    private String retrievalMode;

    private Integer topK;

    private Integer candidateK;

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

    public Boolean getEnableAnswerGeneration() {
        return enableAnswerGeneration;
    }

    public StartEvalRunRequest setEnableAnswerGeneration(Boolean enableAnswerGeneration) {
        this.enableAnswerGeneration = enableAnswerGeneration;
        return this;
    }
}
