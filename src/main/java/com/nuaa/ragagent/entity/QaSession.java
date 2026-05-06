package com.nuaa.ragagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("qa_session")
public class QaSession {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long spaceId;

    private String title;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public QaSession setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public QaSession setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public QaSession setTitle(String title) {
        this.title = title;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public QaSession setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public QaSession setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public QaSession setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}