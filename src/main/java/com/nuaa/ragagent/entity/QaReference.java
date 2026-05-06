package com.nuaa.ragagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("qa_reference")
public class QaReference {

    @TableId(value = "id", type = IdType.AUTO)
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

    public QaReference setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getMessageId() {
        return messageId;
    }

    public QaReference setMessageId(Long messageId) {
        this.messageId = messageId;
        return this;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public QaReference setSessionId(Long sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public Integer getReferenceIndex() {
        return referenceIndex;
    }

    public QaReference setReferenceIndex(Integer referenceIndex) {
        this.referenceIndex = referenceIndex;
        return this;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public QaReference setChunkId(Long chunkId) {
        this.chunkId = chunkId;
        return this;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public QaReference setDocumentId(Long documentId) {
        this.documentId = documentId;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public QaReference setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public QaReference setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
        return this;
    }

    public Double getScore() {
        return score;
    }

    public QaReference setScore(Double score) {
        this.score = score;
        return this;
    }

    public String getContentPreview() {
        return contentPreview;
    }

    public QaReference setContentPreview(String contentPreview) {
        this.contentPreview = contentPreview;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public QaReference setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}