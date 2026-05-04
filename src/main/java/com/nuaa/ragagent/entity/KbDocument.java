package com.nuaa.ragagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("kb_document")
public class KbDocument {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long spaceId;

    private String title;

    private String content;

    private String sourceType;

    private String sourceUri;

    private Integer status;

    private Integer chunkCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public KbDocument setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public KbDocument setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public KbDocument setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getContent() {
        return content;
    }

    public KbDocument setContent(String content) {
        this.content = content;
        return this;
    }

    public String getSourceType() {
        return sourceType;
    }

    public KbDocument setSourceType(String sourceType) {
        this.sourceType = sourceType;
        return this;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public KbDocument setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public KbDocument setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public KbDocument setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public KbDocument setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public KbDocument setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}