package com.nuaa.ragagent.response;

import java.util.List;

public class QaHistoryResponse {

    private QaSessionResponse session;

    private List<QaMessageResponse> messages;

    public QaSessionResponse getSession() {
        return session;
    }

    public QaHistoryResponse setSession(QaSessionResponse session) {
        this.session = session;
        return this;
    }

    public List<QaMessageResponse> getMessages() {
        return messages;
    }

    public QaHistoryResponse setMessages(List<QaMessageResponse> messages) {
        this.messages = messages;
        return this;
    }
}