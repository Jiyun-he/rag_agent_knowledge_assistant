package com.nuaa.ragagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("eval_case_result")
public class EvalCaseResult {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long caseId;

    private String question;

    private String answer;

    private String retrievedChunkIds;

    private String referenceChunkIds;

    private Double recallAtK;

    private Integer hitAtK;

    private Double mrr;

    private Double answerKeywordHit;

    private Integer citationCorrect;

    private Integer grounded;

    private Long latencyMs;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public EvalCaseResult setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getRunId() {
        return runId;
    }

    public EvalCaseResult setRunId(Long runId) {
        this.runId = runId;
        return this;
    }

    public Long getCaseId() {
        return caseId;
    }

    public EvalCaseResult setCaseId(Long caseId) {
        this.caseId = caseId;
        return this;
    }

    public String getQuestion() {
        return question;
    }

    public EvalCaseResult setQuestion(String question) {
        this.question = question;
        return this;
    }

    public String getAnswer() {
        return answer;
    }

    public EvalCaseResult setAnswer(String answer) {
        this.answer = answer;
        return this;
    }

    public String getRetrievedChunkIds() {
        return retrievedChunkIds;
    }

    public EvalCaseResult setRetrievedChunkIds(String retrievedChunkIds) {
        this.retrievedChunkIds = retrievedChunkIds;
        return this;
    }

    public String getReferenceChunkIds() {
        return referenceChunkIds;
    }

    public EvalCaseResult setReferenceChunkIds(String referenceChunkIds) {
        this.referenceChunkIds = referenceChunkIds;
        return this;
    }

    public Double getRecallAtK() {
        return recallAtK;
    }

    public EvalCaseResult setRecallAtK(Double recallAtK) {
        this.recallAtK = recallAtK;
        return this;
    }

    public Integer getHitAtK() {
        return hitAtK;
    }

    public EvalCaseResult setHitAtK(Integer hitAtK) {
        this.hitAtK = hitAtK;
        return this;
    }

    public Double getMrr() {
        return mrr;
    }

    public EvalCaseResult setMrr(Double mrr) {
        this.mrr = mrr;
        return this;
    }

    public Double getAnswerKeywordHit() {
        return answerKeywordHit;
    }

    public EvalCaseResult setAnswerKeywordHit(Double answerKeywordHit) {
        this.answerKeywordHit = answerKeywordHit;
        return this;
    }

    public Integer getCitationCorrect() {
        return citationCorrect;
    }

    public EvalCaseResult setCitationCorrect(Integer citationCorrect) {
        this.citationCorrect = citationCorrect;
        return this;
    }

    public Integer getGrounded() {
        return grounded;
    }

    public EvalCaseResult setGrounded(Integer grounded) {
        this.grounded = grounded;
        return this;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public EvalCaseResult setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public EvalCaseResult setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public EvalCaseResult setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public EvalCaseResult setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}