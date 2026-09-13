package com.nuaa.ragagent.request;
/**
 * @author jiyunhe
 */

public class AskRequest {

    private Long spaceId;

    private Long sessionId;

    private String question;

    private Integer topK;

    private String retrievalMode;

    private Integer candidateK;

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

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public AskRequest setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
        return this;
    }

    public Integer getCandidateK() {
        return candidateK;
    }

    public AskRequest setCandidateK(Integer candidateK) {
        this.candidateK = candidateK;
        return this;
    }
}
