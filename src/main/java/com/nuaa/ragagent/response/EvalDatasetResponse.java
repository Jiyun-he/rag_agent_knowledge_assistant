package com.nuaa.ragagent.response;

import java.time.LocalDateTime;

public class EvalDatasetResponse {

    private Long id;

    private Long spaceId;

    private String name;

    private String description;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public EvalDatasetResponse setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public EvalDatasetResponse setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getName() {
        return name;
    }

    public EvalDatasetResponse setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public EvalDatasetResponse setDescription(String description) {
        this.description = description;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public EvalDatasetResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public EvalDatasetResponse setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public EvalDatasetResponse setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}