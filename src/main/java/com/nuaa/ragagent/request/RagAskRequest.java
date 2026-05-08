package com.nuaa.ragagent.request;

public class RagAskRequest {

    private Long spaceId;

    private String question;

    private Integer topK;

    public Long getSpaceId() {
        return spaceId;
    }

    public RagAskRequest setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getQuestion() {
        return question;
    }

    public RagAskRequest setQuestion(String question) {
        this.question = question;
        return this;
    }

    public Integer getTopK() {
        return topK;
    }

    public RagAskRequest setTopK(Integer topK) {
        this.topK = topK;
        return this;
    }
}