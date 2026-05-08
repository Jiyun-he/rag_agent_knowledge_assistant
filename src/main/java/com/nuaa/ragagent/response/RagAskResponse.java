package com.nuaa.ragagent.response;

import java.util.List;

public class RagAskResponse {

    private Long spaceId;

    private String question;

    private String answer;

    private Boolean hasContext;

    private String noAnswerReason;

    private Integer topK;

    private Integer matchedCount;

    private Boolean cacheHit;

    private List<RagMatchedChunkResponse> matchedChunks;

    public Long getSpaceId() {
        return spaceId;
    }

    public RagAskResponse setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getQuestion() {
        return question;
    }

    public RagAskResponse setQuestion(String question) {
        this.question = question;
        return this;
    }

    public String getAnswer() {
        return answer;
    }

    public RagAskResponse setAnswer(String answer) {
        this.answer = answer;
        return this;
    }

    public Boolean getHasContext() {
        return hasContext;
    }

    public RagAskResponse setHasContext(Boolean hasContext) {
        this.hasContext = hasContext;
        return this;
    }

    public String getNoAnswerReason() {
        return noAnswerReason;
    }

    public RagAskResponse setNoAnswerReason(String noAnswerReason) {
        this.noAnswerReason = noAnswerReason;
        return this;
    }

    public Integer getTopK() {
        return topK;
    }

    public RagAskResponse setTopK(Integer topK) {
        this.topK = topK;
        return this;
    }

    public Integer getMatchedCount() {
        return matchedCount;
    }

    public RagAskResponse setMatchedCount(Integer matchedCount) {
        this.matchedCount = matchedCount;
        return this;
    }

    public Boolean getCacheHit() {
        return cacheHit;
    }

    public RagAskResponse setCacheHit(Boolean cacheHit) {
        this.cacheHit = cacheHit;
        return this;
    }

    public List<RagMatchedChunkResponse> getMatchedChunks() {
        return matchedChunks;
    }

    public RagAskResponse setMatchedChunks(List<RagMatchedChunkResponse> matchedChunks) {
        this.matchedChunks = matchedChunks;
        return this;
    }
}