package com.nuaa.ragagent.response;

public class EvalRunCompareItemResponse {

    private Integer orderIndex;

    private Long runId;

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

    private Double deltaAvgRecallAtK;

    private Double deltaHitRateAtK;

    private Double deltaMrr;

    private Double deltaAvgLatencyMs;

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public EvalRunCompareItemResponse setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
        return this;
    }

    public Long getRunId() {
        return runId;
    }

    public EvalRunCompareItemResponse setRunId(Long runId) {
        this.runId = runId;
        return this;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public EvalRunCompareItemResponse setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
        return this;
    }

    public String getRunName() {
        return runName;
    }

    public EvalRunCompareItemResponse setRunName(String runName) {
        this.runName = runName;
        return this;
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public EvalRunCompareItemResponse setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
        return this;
    }

    public Integer getTopK() {
        return topK;
    }

    public EvalRunCompareItemResponse setTopK(Integer topK) {
        this.topK = topK;
        return this;
    }

    public Integer getCandidateK() {
        return candidateK;
    }

    public EvalRunCompareItemResponse setCandidateK(Integer candidateK) {
        this.candidateK = candidateK;
        return this;
    }

    public Double getVectorWeight() {
        return vectorWeight;
    }

    public EvalRunCompareItemResponse setVectorWeight(Double vectorWeight) {
        this.vectorWeight = vectorWeight;
        return this;
    }

    public Double getKeywordWeight() {
        return keywordWeight;
    }

    public EvalRunCompareItemResponse setKeywordWeight(Double keywordWeight) {
        this.keywordWeight = keywordWeight;
        return this;
    }

    public Boolean getEnableAnswerGeneration() {
        return enableAnswerGeneration;
    }

    public EvalRunCompareItemResponse setEnableAnswerGeneration(Boolean enableAnswerGeneration) {
        this.enableAnswerGeneration = enableAnswerGeneration;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public EvalRunCompareItemResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Integer getTotalCaseCount() {
        return totalCaseCount;
    }

    public EvalRunCompareItemResponse setTotalCaseCount(Integer totalCaseCount) {
        this.totalCaseCount = totalCaseCount;
        return this;
    }

    public Integer getSuccessCaseCount() {
        return successCaseCount;
    }

    public EvalRunCompareItemResponse setSuccessCaseCount(Integer successCaseCount) {
        this.successCaseCount = successCaseCount;
        return this;
    }

    public Integer getFailedCaseCount() {
        return failedCaseCount;
    }

    public EvalRunCompareItemResponse setFailedCaseCount(Integer failedCaseCount) {
        this.failedCaseCount = failedCaseCount;
        return this;
    }

    public Double getAvgRecallAtK() {
        return avgRecallAtK;
    }

    public EvalRunCompareItemResponse setAvgRecallAtK(Double avgRecallAtK) {
        this.avgRecallAtK = avgRecallAtK;
        return this;
    }

    public Double getHitRateAtK() {
        return hitRateAtK;
    }

    public EvalRunCompareItemResponse setHitRateAtK(Double hitRateAtK) {
        this.hitRateAtK = hitRateAtK;
        return this;
    }

    public Double getMrr() {
        return mrr;
    }

    public EvalRunCompareItemResponse setMrr(Double mrr) {
        this.mrr = mrr;
        return this;
    }

    public Double getAvgAnswerKeywordHit() {
        return avgAnswerKeywordHit;
    }

    public EvalRunCompareItemResponse setAvgAnswerKeywordHit(Double avgAnswerKeywordHit) {
        this.avgAnswerKeywordHit = avgAnswerKeywordHit;
        return this;
    }

    public Double getCitationCorrectRate() {
        return citationCorrectRate;
    }

    public EvalRunCompareItemResponse setCitationCorrectRate(Double citationCorrectRate) {
        this.citationCorrectRate = citationCorrectRate;
        return this;
    }

    public Double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public EvalRunCompareItemResponse setAvgLatencyMs(Double avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
        return this;
    }

    public Double getDeltaAvgRecallAtK() {
        return deltaAvgRecallAtK;
    }

    public EvalRunCompareItemResponse setDeltaAvgRecallAtK(Double deltaAvgRecallAtK) {
        this.deltaAvgRecallAtK = deltaAvgRecallAtK;
        return this;
    }

    public Double getDeltaHitRateAtK() {
        return deltaHitRateAtK;
    }

    public EvalRunCompareItemResponse setDeltaHitRateAtK(Double deltaHitRateAtK) {
        this.deltaHitRateAtK = deltaHitRateAtK;
        return this;
    }

    public Double getDeltaMrr() {
        return deltaMrr;
    }

    public EvalRunCompareItemResponse setDeltaMrr(Double deltaMrr) {
        this.deltaMrr = deltaMrr;
        return this;
    }

    public Double getDeltaAvgLatencyMs() {
        return deltaAvgLatencyMs;
    }

    public EvalRunCompareItemResponse setDeltaAvgLatencyMs(Double deltaAvgLatencyMs) {
        this.deltaAvgLatencyMs = deltaAvgLatencyMs;
        return this;
    }
}