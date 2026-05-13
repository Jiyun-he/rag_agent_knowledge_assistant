package com.nuaa.ragagent.response;

import java.util.List;

public class EvalRunCompareResponse {

    private Long baselineRunId;

    private Integer comparedRunCount;

    private Long bestRecallRunId;

    private Long bestHitRateRunId;

    private Long bestMrrRunId;

    private Long fastestRunId;

    private String summary;

    private List<EvalRunCompareItemResponse> items;

    public Long getBaselineRunId() {
        return baselineRunId;
    }

    public EvalRunCompareResponse setBaselineRunId(Long baselineRunId) {
        this.baselineRunId = baselineRunId;
        return this;
    }

    public Integer getComparedRunCount() {
        return comparedRunCount;
    }

    public EvalRunCompareResponse setComparedRunCount(Integer comparedRunCount) {
        this.comparedRunCount = comparedRunCount;
        return this;
    }

    public Long getBestRecallRunId() {
        return bestRecallRunId;
    }

    public EvalRunCompareResponse setBestRecallRunId(Long bestRecallRunId) {
        this.bestRecallRunId = bestRecallRunId;
        return this;
    }

    public Long getBestHitRateRunId() {
        return bestHitRateRunId;
    }

    public EvalRunCompareResponse setBestHitRateRunId(Long bestHitRateRunId) {
        this.bestHitRateRunId = bestHitRateRunId;
        return this;
    }

    public Long getBestMrrRunId() {
        return bestMrrRunId;
    }

    public EvalRunCompareResponse setBestMrrRunId(Long bestMrrRunId) {
        this.bestMrrRunId = bestMrrRunId;
        return this;
    }

    public Long getFastestRunId() {
        return fastestRunId;
    }

    public EvalRunCompareResponse setFastestRunId(Long fastestRunId) {
        this.fastestRunId = fastestRunId;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public EvalRunCompareResponse setSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public List<EvalRunCompareItemResponse> getItems() {
        return items;
    }

    public EvalRunCompareResponse setItems(List<EvalRunCompareItemResponse> items) {
        this.items = items;
        return this;
    }
}