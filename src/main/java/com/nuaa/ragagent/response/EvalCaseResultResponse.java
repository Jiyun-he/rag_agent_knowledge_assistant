package com.nuaa.ragagent.response;

public class EvalCaseResultResponse {

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

    public Long getId() {
        return id;
    }

    public EvalCaseResultResponse setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getRunId() {
        return runId;
    }

    public EvalCaseResultResponse setRunId(Long runId) {
        this.runId = runId;
        return this;
    }

    public Long getCaseId() {
        return caseId;
    }

    public EvalCaseResultResponse setCaseId(Long caseId) {
        this.caseId = caseId;
        return this;
    }

    public String getQuestion() {
        return question;
    }

    public EvalCaseResultResponse setQuestion(String question) {
        this.question = question;
        return this;
    }

    public String getAnswer() {
        return answer;
    }

    public EvalCaseResultResponse setAnswer(String answer) {
        this.answer = answer;
        return this;
    }

    public String getRetrievedChunkIds() {
        return retrievedChunkIds;
    }

    public EvalCaseResultResponse setRetrievedChunkIds(String retrievedChunkIds) {
        this.retrievedChunkIds = retrievedChunkIds;
        return this;
    }

    public String getReferenceChunkIds() {
        return referenceChunkIds;
    }

    public EvalCaseResultResponse setReferenceChunkIds(String referenceChunkIds) {
        this.referenceChunkIds = referenceChunkIds;
        return this;
    }

    public Double getRecallAtK() {
        return recallAtK;
    }

    public EvalCaseResultResponse setRecallAtK(Double recallAtK) {
        this.recallAtK = recallAtK;
        return this;
    }

    public Integer getHitAtK() {
        return hitAtK;
    }

    public EvalCaseResultResponse setHitAtK(Integer hitAtK) {
        this.hitAtK = hitAtK;
        return this;
    }

    public Double getMrr() {
        return mrr;
    }

    public EvalCaseResultResponse setMrr(Double mrr) {
        this.mrr = mrr;
        return this;
    }

    public Double getAnswerKeywordHit() {
        return answerKeywordHit;
    }

    public EvalCaseResultResponse setAnswerKeywordHit(Double answerKeywordHit) {
        this.answerKeywordHit = answerKeywordHit;
        return this;
    }

    public Integer getCitationCorrect() {
        return citationCorrect;
    }

    public EvalCaseResultResponse setCitationCorrect(Integer citationCorrect) {
        this.citationCorrect = citationCorrect;
        return this;
    }

    public Integer getGrounded() {
        return grounded;
    }

    public EvalCaseResultResponse setGrounded(Integer grounded) {
        this.grounded = grounded;
        return this;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public EvalCaseResultResponse setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public EvalCaseResultResponse setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }
}