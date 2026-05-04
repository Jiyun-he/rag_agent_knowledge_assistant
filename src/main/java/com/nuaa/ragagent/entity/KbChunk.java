package com.nuaa.ragagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("kb_chunk")
public class KbChunk {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long spaceId;

    private Integer chunkIndex;

    private String content;

    private Integer charCount;

    private Integer embeddingStatus;

    private String vectorId;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public KbChunk setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public KbChunk setDocumentId(Long documentId) {
        this.documentId = documentId;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public KbChunk setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public KbChunk setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
        return this;
    }

    public String getContent() {
        return content;
    }

    public KbChunk setContent(String content) {
        this.content = content;
        return this;
    }

    public Integer getCharCount() {
        return charCount;
    }

    public KbChunk setCharCount(Integer charCount) {
        this.charCount = charCount;
        return this;
    }

    public Integer getEmbeddingStatus() {
        return embeddingStatus;
    }

    public KbChunk setEmbeddingStatus(Integer embeddingStatus) {
        this.embeddingStatus = embeddingStatus;
        return this;
    }

    public String getVectorId() {
        return vectorId;
    }

    public KbChunk setVectorId(String vectorId) {
        this.vectorId = vectorId;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public KbChunk setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public KbChunk setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public KbChunk setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}