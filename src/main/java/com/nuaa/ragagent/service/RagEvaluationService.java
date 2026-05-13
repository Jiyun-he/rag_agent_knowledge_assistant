package com.nuaa.ragagent.service;

import com.nuaa.ragagent.request.CompareEvalRunsRequest;
import com.nuaa.ragagent.request.CreateEvalCaseRequest;
import com.nuaa.ragagent.request.CreateEvalDatasetRequest;
import com.nuaa.ragagent.request.StartEvalRunRequest;
import com.nuaa.ragagent.response.EvalCaseResponse;
import com.nuaa.ragagent.response.EvalCaseResultResponse;
import com.nuaa.ragagent.response.EvalDatasetResponse;
import com.nuaa.ragagent.response.EvalRunCompareResponse;
import com.nuaa.ragagent.response.EvalRunResponse;

import java.util.List;

public interface RagEvaluationService {

    EvalDatasetResponse createDataset(CreateEvalDatasetRequest request);

    List<EvalDatasetResponse> listDatasets();

    EvalDatasetResponse getDataset(Long datasetId);

    EvalCaseResponse createCase(Long datasetId, CreateEvalCaseRequest request);

    List<EvalCaseResponse> listCases(Long datasetId);

    EvalRunResponse startRun(StartEvalRunRequest request);

    EvalRunResponse getRun(Long runId);

    List<EvalCaseResultResponse> listRunResults(Long runId);

    EvalRunCompareResponse compareRuns(CompareEvalRunsRequest request);
}