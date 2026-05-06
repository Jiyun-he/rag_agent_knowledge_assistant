package com.nuaa.ragagent.response;

import java.time.LocalDateTime;

public class QaSessionResponse {

    private Long id;

    private Long spaceId;

    private String title;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public QaSessionResponse setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public QaSessionResponse setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public QaSessionResponse setTitle(String title) {
        this.title = title;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public QaSessionResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public QaSessionResponse setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public QaSessionResponse setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}