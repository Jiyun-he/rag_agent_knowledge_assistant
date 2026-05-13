package com.nuaa.ragagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("eval_dataset")
public class EvalDataset {

    @TableId(value = "id", type = IdType.AUTO)
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

    public EvalDataset setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public EvalDataset setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getName() {
        return name;
    }

    public EvalDataset setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public EvalDataset setDescription(String description) {
        this.description = description;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public EvalDataset setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public EvalDataset setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public EvalDataset setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}