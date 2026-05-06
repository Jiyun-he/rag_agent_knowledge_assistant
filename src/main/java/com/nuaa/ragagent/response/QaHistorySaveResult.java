package com.nuaa.ragagent.response;

public class QaHistorySaveResult {

    private Long sessionId;

    private Long userMessageId;

    private Long assistantMessageId;

    public Long getSessionId() {
        return sessionId;
    }

    public QaHistorySaveResult setSessionId(Long sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public Long getUserMessageId() {
        return userMessageId;
    }

    public QaHistorySaveResult setUserMessageId(Long userMessageId) {
        this.userMessageId = userMessageId;
        return this;
    }

    public Long getAssistantMessageId() {
        return assistantMessageId;
    }

    public QaHistorySaveResult setAssistantMessageId(Long assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
        return this;
    }
}