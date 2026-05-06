package com.nuaa.ragagent.request;

public class AskRequest {

    private Long spaceId;

    private Long sessionId;

    private String question;

    private Integer topK;

    public Long getSpaceId() {
        return spaceId;
    }

    public AskRequest setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public AskRequest setSessionId(Long sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getQuestion() {
        return question;
    }

    public AskRequest setQuestion(String question) {
        this.question = question;
        return this;
    }

    public Integer getTopK() {
        return topK;
    }

    public AskRequest setTopK(Integer topK) {
        this.topK = topK;
        return this;
    }
}