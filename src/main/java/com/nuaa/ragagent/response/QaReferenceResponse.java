package com.nuaa.ragagent.response;

import java.time.LocalDateTime;

public class QaReferenceResponse {

    private Long id;

    private Long messageId;

    private Long sessionId;

    private Integer referenceIndex;

    private Long chunkId;

    private Long documentId;

    private Long spaceId;

    private Integer chunkIndex;

    private Double score;

    private String contentPreview;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public QaReferenceResponse setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getMessageId() {
        return messageId;
    }

    public QaReferenceResponse setMessageId(Long messageId) {
        this.messageId = messageId;
        return this;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public QaReferenceResponse setSessionId(Long sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public Integer getReferenceIndex() {
        return referenceIndex;
    }

    public QaReferenceResponse setReferenceIndex(Integer referenceIndex) {
        this.referenceIndex = referenceIndex;
        return this;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public QaReferenceResponse setChunkId(Long chunkId) {
        this.chunkId = chunkId;
        return this;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public QaReferenceResponse setDocumentId(Long documentId) {
        this.documentId = documentId;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public QaReferenceResponse setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public QaReferenceResponse setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
        return this;
    }

    public Double getScore() {
        return score;
    }

    public QaReferenceResponse setScore(Double score) {
        this.score = score;
        return this;
    }

    public String getContentPreview() {
        return contentPreview;
    }

    public QaReferenceResponse setContentPreview(String contentPreview) {
        this.contentPreview = contentPreview;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public QaReferenceResponse setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}