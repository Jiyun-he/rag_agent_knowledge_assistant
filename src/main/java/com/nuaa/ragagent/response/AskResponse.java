package com.nuaa.ragagent.response;

import java.util.List;

public class AskResponse {

    private Long spaceId;

    private Long sessionId;

    private Long userMessageId;

    private Long assistantMessageId;

    private String question;

    private String answer;

    private Integer referenceCount;

    private List<RagAnswerReferenceResponse> references;

    public Long getSpaceId() {
        return spaceId;
    }

    public AskResponse setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public AskResponse setSessionId(Long sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public Long getUserMessageId() {
        return userMessageId;
    }

    public AskResponse setUserMessageId(Long userMessageId) {
        this.userMessageId = userMessageId;
        return this;
    }

    public Long getAssistantMessageId() {
        return assistantMessageId;
    }

    public AskResponse setAssistantMessageId(Long assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
        return this;
    }

    public String getQuestion() {
        return question;
    }

    public AskResponse setQuestion(String question) {
        this.question = question;
        return this;
    }

    public String getAnswer() {
        return answer;
    }

    public AskResponse setAnswer(String answer) {
        this.answer = answer;
        return this;
    }

    public Integer getReferenceCount() {
        return referenceCount;
    }

    public AskResponse setReferenceCount(Integer referenceCount) {
        this.referenceCount = referenceCount;
        return this;
    }

    public List<RagAnswerReferenceResponse> getReferences() {
        return references;
    }

    public AskResponse setReferences(List<RagAnswerReferenceResponse> references) {
        this.references = references;
        return this;
    }
}