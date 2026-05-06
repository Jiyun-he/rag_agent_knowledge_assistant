package com.nuaa.ragagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("qa_message")
public class QaMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long spaceId;

    private String role;

    private String content;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public QaMessage setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public QaMessage setSessionId(Long sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public QaMessage setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getRole() {
        return role;
    }

    public QaMessage setRole(String role) {
        this.role = role;
        return this;
    }

    public String getContent() {
        return content;
    }

    public QaMessage setContent(String content) {
        this.content = content;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public QaMessage setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}