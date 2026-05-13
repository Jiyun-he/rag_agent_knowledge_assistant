package com.nuaa.ragagent.controller;

import com.nuaa.ragagent.common.ApiResponse;
import com.nuaa.ragagent.request.CreateEvalCaseRequest;
import com.nuaa.ragagent.request.CreateEvalDatasetRequest;
import com.nuaa.ragagent.request.StartEvalRunRequest;
import com.nuaa.ragagent.response.EvalCaseResponse;
import com.nuaa.ragagent.response.EvalCaseResultResponse;
import com.nuaa.ragagent.response.EvalDatasetResponse;
import com.nuaa.ragagent.response.EvalRunResponse;
import com.nuaa.ragagent.service.RagEvaluationService;
import org.springframework.web.bind.annotation.*;
import com.nuaa.ragagent.request.CompareEvalRunsRequest;
import com.nuaa.ragagent.response.EvalRunCompareResponse;

import java.util.List;

@RestController
@RequestMapping("/api/rag/eval")
public class RagEvaluationController {

    private final RagEvaluationService ragEvaluationService;

    public RagEvaluationController(RagEvaluationService ragEvaluationService) {
        this.ragEvaluationService = ragEvaluationService;
    }

    @PostMapping("/datasets")
    public ApiResponse<EvalDatasetResponse> createDataset(@RequestBody CreateEvalDatasetRequest request) {
        return ApiResponse.success(ragEvaluationService.createDataset(request));
    }

    @GetMapping("/datasets")
    public ApiResponse<List<EvalDatasetResponse>> listDatasets() {
        return ApiResponse.success(ragEvaluationService.listDatasets());
    }

    @GetMapping("/datasets/{datasetId}")
    public ApiResponse<EvalDatasetResponse> getDataset(@PathVariable Long datasetId) {
        return ApiResponse.success(ragEvaluationService.getDataset(datasetId));
    }

    @PostMapping("/datasets/{datasetId}/cases")
    public ApiResponse<EvalCaseResponse> createCase(@PathVariable Long datasetId,
                                                    @RequestBody CreateEvalCaseRequest request) {
        return ApiResponse.success(ragEvaluationService.createCase(datasetId, request));
    }

    @GetMapping("/datasets/{datasetId}/cases")
    public ApiResponse<List<EvalCaseResponse>> listCases(@PathVariable Long datasetId) {
        return ApiResponse.success(ragEvaluationService.listCases(datasetId));
    }

    @PostMapping("/runs")
    public ApiResponse<EvalRunResponse> startRun(@RequestBody StartEvalRunRequest request) {
        return ApiResponse.success(ragEvaluationService.startRun(request));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<EvalRunResponse> getRun(@PathVariable Long runId) {
        return ApiResponse.success(ragEvaluationService.getRun(runId));
    }

    @GetMapping("/runs/{runId}/results")
    public ApiResponse<List<EvalCaseResultResponse>> listRunResults(@PathVariable Long runId) {
        return ApiResponse.success(ragEvaluationService.listRunResults(runId));
    }

    @PostMapping("/runs/compare")
    public ApiResponse<EvalRunCompareResponse> compareRuns(@RequestBody CompareEvalRunsRequest request) {
        return ApiResponse.success(ragEvaluationService.compareRuns(request));
    }
}