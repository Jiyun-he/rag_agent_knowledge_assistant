package com.nuaa.ragagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("eval_run")
public class EvalRun {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private String runName;

    private String retrievalMode;

    private Integer topK;

    private Integer candidateK;

    private Double vectorWeight;

    private Double keywordWeight;

    private Integer enableAnswerGeneration;

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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public EvalRun setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public EvalRun setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
        return this;
    }

    public String getRunName() {
        return runName;
    }

    public EvalRun setRunName(String runName) {
        this.runName = runName;
        return this;
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public EvalRun setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
        return this;
    }

    public Integer getTopK() {
        return topK;
    }

    public EvalRun setTopK(Integer topK) {
        this.topK = topK;
        return this;
    }

    public Integer getCandidateK() {
        return candidateK;
    }

    public EvalRun setCandidateK(Integer candidateK) {
        this.candidateK = candidateK;
        return this;
    }

    public Double getVectorWeight() {
        return vectorWeight;
    }

    public EvalRun setVectorWeight(Double vectorWeight) {
        this.vectorWeight = vectorWeight;
        return this;
    }

    public Double getKeywordWeight() {
        return keywordWeight;
    }

    public EvalRun setKeywordWeight(Double keywordWeight) {
        this.keywordWeight = keywordWeight;
        return this;
    }

    public Integer getEnableAnswerGeneration() {
        return enableAnswerGeneration;
    }

    public EvalRun setEnableAnswerGeneration(Integer enableAnswerGeneration) {
        this.enableAnswerGeneration = enableAnswerGeneration;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public EvalRun setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Integer getTotalCaseCount() {
        return totalCaseCount;
    }

    public EvalRun setTotalCaseCount(Integer totalCaseCount) {
        this.totalCaseCount = totalCaseCount;
        return this;
    }

    public Integer getSuccessCaseCount() {
        return successCaseCount;
    }

    public EvalRun setSuccessCaseCount(Integer successCaseCount) {
        this.successCaseCount = successCaseCount;
        return this;
    }

    public Integer getFailedCaseCount() {
        return failedCaseCount;
    }

    public EvalRun setFailedCaseCount(Integer failedCaseCount) {
        this.failedCaseCount = failedCaseCount;
        return this;
    }

    public Double getAvgRecallAtK() {
        return avgRecallAtK;
    }

    public EvalRun setAvgRecallAtK(Double avgRecallAtK) {
        this.avgRecallAtK = avgRecallAtK;
        return this;
    }

    public Double getHitRateAtK() {
        return hitRateAtK;
    }

    public EvalRun setHitRateAtK(Double hitRateAtK) {
        this.hitRateAtK = hitRateAtK;
        return this;
    }

    public Double getMrr() {
        return mrr;
    }

    public EvalRun setMrr(Double mrr) {
        this.mrr = mrr;
        return this;
    }

    public Double getAvgAnswerKeywordHit() {
        return avgAnswerKeywordHit;
    }

    public EvalRun setAvgAnswerKeywordHit(Double avgAnswerKeywordHit) {
        this.avgAnswerKeywordHit = avgAnswerKeywordHit;
        return this;
    }

    public Double getCitationCorrectRate() {
        return citationCorrectRate;
    }

    public EvalRun setCitationCorrectRate(Double citationCorrectRate) {
        this.citationCorrectRate = citationCorrectRate;
        return this;
    }

    public Double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public EvalRun setAvgLatencyMs(Double avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public EvalRun setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public EvalRun setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public EvalRun setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public EvalRun setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public EvalRun setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}