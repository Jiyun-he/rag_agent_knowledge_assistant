package com.nuaa.ragagent.response;

import java.time.LocalDateTime;

public class EvalRunResponse {

    private Long id;

    private Long datasetId;

    private String runName;

    private String retrievalMode;

    private Integer topK;

    private Integer candidateK;

    private Double vectorWeight;

    private Double keywordWeight;

    private Boolean enableAnswerGeneration;

    private Integer status;

    private Integer totalCaseCount;

    private Integer successCaseCount;

    private Integer failedCaseCount;

    private Double avgRecallAtK;

    private Double hitRateAtK;

    private Double mrr;

    private Double avgAnswerKeywordHit;

    private Double citationCorrectRate;

    private Double avgLatencyMs;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    public Long getId() {
        return id;
    }

    public EvalRunResponse setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public EvalRunResponse setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
        return this;
    }

    public String getRunName() {
        return runName;
    }

    public EvalRunResponse setRunName(String runName) {
        this.runName = runName;
        return this;
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public EvalRunResponse setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
        return this;
    }

    public Integer getTopK() {
        return topK;
    }

    public EvalRunResponse setTopK(Integer topK) {
        this.topK = topK;
        return this;
    }

    public Integer getCandidateK() {
        return candidateK;
    }

    public EvalRunResponse setCandidateK(Integer candidateK) {
        this.candidateK = candidateK;
        return this;
    }

    public Double getVectorWeight() {
        return vectorWeight;
    }

    public EvalRunResponse setVectorWeight(Double vectorWeight) {
        this.vectorWeight = vectorWeight;
        return this;
    }

    public Double getKeywordWeight() {
        return keywordWeight;
    }

    public EvalRunResponse setKeywordWeight(Double keywordWeight) {
        this.keywordWeight = keywordWeight;
        return this;
    }

    public Boolean getEnableAnswerGeneration() {
        return enableAnswerGeneration;
    }

    public EvalRunResponse setEnableAnswerGeneration(Boolean enableAnswerGeneration) {
        this.enableAnswerGeneration = enableAnswerGeneration;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public EvalRunResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Integer getTotalCaseCount() {
        return totalCaseCount;
    }

    public EvalRunResponse setTotalCaseCount(Integer totalCaseCount) {
        this.totalCaseCount = totalCaseCount;
        return this;
    }

    public Integer getSuccessCaseCount() {
        return successCaseCount;
    }

    public EvalRunResponse setSuccessCaseCount(Integer successCaseCount) {
        this.successCaseCount = successCaseCount;
        return this;
    }

    public Integer getFailedCaseCount() {
        return failedCaseCount;
    }

    public EvalRunResponse setFailedCaseCount(Integer failedCaseCount) {
        this.failedCaseCount = failedCaseCount;
        return this;
    }

    public Double getAvgRecallAtK() {
        return avgRecallAtK;
    }

    public EvalRunResponse setAvgRecallAtK(Double avgRecallAtK) {
        this.avgRecallAtK = avgRecallAtK;
        return this;
    }

    public Double getHitRateAtK() {
        return hitRateAtK;
    }

    public EvalRunResponse setHitRateAtK(Double hitRateAtK) {
        this.hitRateAtK = hitRateAtK;
        return this;
    }

    public Double getMrr() {
        return mrr;
    }

    public EvalRunResponse setMrr(Double mrr) {
        this.mrr = mrr;
        return this;
    }

    public Double getAvgAnswerKeywordHit() {
        return avgAnswerKeywordHit;
    }

    public EvalRunResponse setAvgAnswerKeywordHit(Double avgAnswerKeywordHit) {
        this.avgAnswerKeywordHit = avgAnswerKeywordHit;
        return this;
    }

    public Double getCitationCorrectRate() {
        return citationCorrectRate;
    }

    public EvalRunResponse setCitationCorrectRate(Double citationCorrectRate) {
        this.citationCorrectRate = citationCorrectRate;
        return this;
    }

    public Double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public EvalRunResponse setAvgLatencyMs(Double avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public EvalRunResponse setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public EvalRunResponse setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public EvalRunResponse setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
        return this;
    }
}