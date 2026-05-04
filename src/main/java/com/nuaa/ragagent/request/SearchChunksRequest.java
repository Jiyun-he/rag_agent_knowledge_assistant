package com.nuaa.ragagent.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SearchChunksRequest {

    @NotNull(message = "spaceId cannot be null")
    private Long spaceId;

    @NotBlank(message = "query cannot be blank")
    @Size(max = 1000, message = "query length cannot exceed 1000")
    private String query;

    private Integer topK = 5;

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}