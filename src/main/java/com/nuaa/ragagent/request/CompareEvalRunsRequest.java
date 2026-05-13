package com.nuaa.ragagent.request;

import java.util.List;

public class CompareEvalRunsRequest {

    private List<Long> runIds;

    public List<Long> getRunIds() {
        return runIds;
    }

    public CompareEvalRunsRequest setRunIds(List<Long> runIds) {
        this.runIds = runIds;
        return this;
    }
}