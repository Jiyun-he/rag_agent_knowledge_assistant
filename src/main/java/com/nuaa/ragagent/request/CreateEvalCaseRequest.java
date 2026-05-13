package com.nuaa.ragagent.request;

import java.util.List;

public class CreateEvalCaseRequest {

    private String question;

    private String expectedAnswer;

    private List<Long> expectedChunkIds;

    private List<String> expectedKeywords;

    private String difficulty;

    public String getQuestion() {
        return question;
    }

    public CreateEvalCaseRequest setQuestion(String question) {
        this.question = question;
        return this;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public CreateEvalCaseRequest setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
        return this;
    }

    public List<Long> getExpectedChunkIds() {
        return expectedChunkIds;
    }

    public CreateEvalCaseRequest setExpectedChunkIds(List<Long> expectedChunkIds) {
        this.expectedChunkIds = expectedChunkIds;
        return this;
    }

    public List<String> getExpectedKeywords() {
        return expectedKeywords;
    }

    public CreateEvalCaseRequest setExpectedKeywords(List<String> expectedKeywords) {
        this.expectedKeywords = expectedKeywords;
        return this;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public CreateEvalCaseRequest setDifficulty(String difficulty) {
        this.difficulty = difficulty;
        return this;
    }
}