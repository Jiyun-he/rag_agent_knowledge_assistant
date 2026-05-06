package com.nuaa.ragagent.response;

import java.time.LocalDateTime;
import java.util.List;

public class QaMessageResponse {

    private Long id;

    private Long sessionId;

    private Long spaceId;

    private String role;

    private String content;

    private LocalDateTime createdAt;

    private List<QaReferenceResponse> references;

    public Long getId() {
        return id;
    }

    public QaMessageResponse setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public QaMessageResponse setSessionId(Long sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public QaMessageResponse setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getRole() {
        return role;
    }

    public QaMessageResponse setRole(String role) {
        this.role = role;
        return this;
    }

    public String getContent() {
        return content;
    }

    public QaMessageResponse setContent(String content) {
        this.content = content;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public QaMessageResponse setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public List<QaReferenceResponse> getReferences() {
        return references;
    }

    public QaMessageResponse setReferences(List<QaReferenceResponse> references) {
        this.references = references;
        return this;
    }
}