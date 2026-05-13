package com.nuaa.ragagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("eval_case")
public class EvalCase {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private String question;

    private String expectedAnswer;

    private String expectedChunkIds;

    private String expectedKeywords;

    private String difficulty;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public EvalCase setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public EvalCase setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
        return this;
    }

    public String getQuestion() {
        return question;
    }

    public EvalCase setQuestion(String question) {
        this.question = question;
        return this;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public EvalCase setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
        return this;
    }

    public String getExpectedChunkIds() {
        return expectedChunkIds;
    }

    public EvalCase setExpectedChunkIds(String expectedChunkIds) {
        this.expectedChunkIds = expectedChunkIds;
        return this;
    }

    public String getExpectedKeywords() {
        return expectedKeywords;
    }

    public EvalCase setExpectedKeywords(String expectedKeywords) {
        this.expectedKeywords = expectedKeywords;
        return this;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public EvalCase setDifficulty(String difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public EvalCase setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public EvalCase setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public EvalCase setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}