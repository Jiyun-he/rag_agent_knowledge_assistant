package com.nuaa.ragagent.response;

import java.time.LocalDateTime;
import java.util.List;

public class EvalCaseResponse {

    private Long id;

    private Long datasetId;

    private String question;

    private String expectedAnswer;

    private List<Long> expectedChunkIds;

    private List<String> expectedKeywords;

    private String difficulty;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public EvalCaseResponse setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public EvalCaseResponse setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
        return this;
    }

    public String getQuestion() {
        return question;
    }

    public EvalCaseResponse setQuestion(String question) {
        this.question = question;
        return this;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public EvalCaseResponse setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
        return this;
    }

    public List<Long> getExpectedChunkIds() {
        return expectedChunkIds;
    }

    public EvalCaseResponse setExpectedChunkIds(List<Long> expectedChunkIds) {
        this.expectedChunkIds = expectedChunkIds;
        return this;
    }

    public List<String> getExpectedKeywords() {
        return expectedKeywords;
    }

    public EvalCaseResponse setExpectedKeywords(List<String> expectedKeywords) {
        this.expectedKeywords = expectedKeywords;
        return this;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public EvalCaseResponse setDifficulty(String difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public EvalCaseResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public EvalCaseResponse setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public EvalCaseResponse setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}