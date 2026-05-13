package com.nuaa.ragagent.request;

public class CreateEvalDatasetRequest {

    private Long spaceId;

    private String name;

    private String description;

    public Long getSpaceId() {
        return spaceId;
    }

    public CreateEvalDatasetRequest setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getName() {
        return name;
    }

    public CreateEvalDatasetRequest setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public CreateEvalDatasetRequest setDescription(String description) {
        this.description = description;
        return this;
    }
}