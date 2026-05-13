package com.nuaa.ragagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuaa.ragagent.entity.EvalCase;
import com.nuaa.ragagent.entity.EvalCaseResult;
import com.nuaa.ragagent.entity.EvalDataset;
import com.nuaa.ragagent.entity.EvalRun;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.mapper.EvalCaseMapper;
import com.nuaa.ragagent.mapper.EvalCaseResultMapper;
import com.nuaa.ragagent.mapper.EvalDatasetMapper;
import com.nuaa.ragagent.mapper.EvalRunMapper;
import com.nuaa.ragagent.request.CreateEvalCaseRequest;
import com.nuaa.ragagent.request.CreateEvalDatasetRequest;
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.request.StartEvalRunRequest;
import com.nuaa.ragagent.response.EvalCaseResponse;
import com.nuaa.ragagent.response.EvalCaseResultResponse;
import com.nuaa.ragagent.response.EvalDatasetResponse;
import com.nuaa.ragagent.response.EvalRunResponse;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.service.RagEvaluationService;
import com.nuaa.ragagent.service.RagRetrievalService;
import com.nuaa.ragagent.util.RagPromptBuilder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.nuaa.ragagent.request.CompareEvalRunsRequest;
import com.nuaa.ragagent.response.EvalRunCompareItemResponse;
import com.nuaa.ragagent.response.EvalRunCompareResponse;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RagEvaluationServiceImpl implements RagEvaluationService {

    private static final int STATUS_NORMAL = 1;

    private static final int RUN_STATUS_PENDING = 0;
    private static final int RUN_STATUS_RUNNING = 1;
    private static final int RUN_STATUS_SUCCESS = 2;
    private static final int RUN_STATUS_FAILED = 3;
    private static final int RUN_STATUS_PARTIAL_SUCCESS = 4;

    private static final int DEFAULT_TOP_K = 5;
    private static final int DEFAULT_CANDIDATE_K = 20;

    private final EvalDatasetMapper evalDatasetMapper;
    private final EvalCaseMapper evalCaseMapper;
    private final EvalRunMapper evalRunMapper;
    private final EvalCaseResultMapper evalCaseResultMapper;
    private final RagRetrievalService ragRetrievalService;
    private final ChatClient chatClient;
    private final RagPromptBuilder ragPromptBuilder;
    private final ObjectMapper objectMapper;

    public RagEvaluationServiceImpl(EvalDatasetMapper evalDatasetMapper,
                                    EvalCaseMapper evalCaseMapper,
                                    EvalRunMapper evalRunMapper,
                                    EvalCaseResultMapper evalCaseResultMapper,
                                    RagRetrievalService ragRetrievalService,
                                    ChatClient chatClient,
                                    RagPromptBuilder ragPromptBuilder,
                                    ObjectMapper objectMapper) {
        this.evalDatasetMapper = evalDatasetMapper;
        this.evalCaseMapper = evalCaseMapper;
        this.evalRunMapper = evalRunMapper;
        this.evalCaseResultMapper = evalCaseResultMapper;
        this.ragRetrievalService = ragRetrievalService;
        this.chatClient = chatClient;
        this.ragPromptBuilder = ragPromptBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvalRunCompareResponse compareRuns(CompareEvalRunsRequest request) {
        validateCompareRunsRequest(request);

        List<Long> orderedRunIds = new ArrayList<>(new LinkedHashSet<>(request.getRunIds()));

        List<EvalRun> runs = evalRunMapper.selectBatchIds(orderedRunIds);

        if (runs.size() != orderedRunIds.size()) {
            throw new BusinessException("部分评测运行不存在，请检查 runIds");
        }

        Map<Long, EvalRun> runMap = runs.stream()
                .collect(Collectors.toMap(EvalRun::getId, Function.identity()));

        EvalRun baselineRun = runMap.get(orderedRunIds.get(0));
        if (baselineRun == null) {
            throw new BusinessException("基准评测运行不存在");
        }

        List<EvalRunCompareItemResponse> items = new ArrayList<>();

        for (int i = 0; i < orderedRunIds.size(); i++) {
            Long runId = orderedRunIds.get(i);
            EvalRun run = runMap.get(runId);

            if (run == null) {
                throw new BusinessException("评测运行不存在，runId = " + runId);
            }

            items.add(toCompareItemResponse(i + 1, run, baselineRun));
        }

        Long bestRecallRunId = findBestRecallRunId(items);
        Long bestHitRateRunId = findBestHitRateRunId(items);
        Long bestMrrRunId = findBestMrrRunId(items);
        Long fastestRunId = findFastestRunId(items);

        String summary = buildCompareSummary(items, bestRecallRunId, bestHitRateRunId, bestMrrRunId, fastestRunId);

        return new EvalRunCompareResponse()
                .setBaselineRunId(baselineRun.getId())
                .setComparedRunCount(items.size())
                .setBestRecallRunId(bestRecallRunId)
                .setBestHitRateRunId(bestHitRateRunId)
                .setBestMrrRunId(bestMrrRunId)
                .setFastestRunId(fastestRunId)
                .setSummary(summary)
                .setItems(items);
    }

    @Override
    public EvalDatasetResponse createDataset(CreateEvalDatasetRequest request) {
        if (request == null) {
            throw new BusinessException("CreateEvalDatasetRequest cannot be null");
        }

        if (request.getSpaceId() == null) {
            throw new BusinessException("spaceId cannot be null");
        }

        if (!StringUtils.hasText(request.getName())) {
            throw new BusinessException("dataset name cannot be blank");
        }

        EvalDataset dataset = new EvalDataset();
        dataset.setSpaceId(request.getSpaceId());
        dataset.setName(request.getName());
        dataset.setDescription(request.getDescription());
        dataset.setStatus(STATUS_NORMAL);
        dataset.setCreatedAt(LocalDateTime.now());
        dataset.setUpdatedAt(LocalDateTime.now());

        evalDatasetMapper.insert(dataset);

        return toDatasetResponse(dataset);
    }

    @Override
    public List<EvalDatasetResponse> listDatasets() {
        LambdaQueryWrapper<EvalDataset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalDataset::getStatus, STATUS_NORMAL);
        wrapper.orderByDesc(EvalDataset::getCreatedAt);

        List<EvalDataset> datasets = evalDatasetMapper.selectList(wrapper);

        List<EvalDatasetResponse> responses = new ArrayList<>();
        for (EvalDataset dataset : datasets) {
            responses.add(toDatasetResponse(dataset));
        }

        return responses;
    }

    @Override
    public EvalDatasetResponse getDataset(Long datasetId) {
        EvalDataset dataset = requireDataset(datasetId);
        return toDatasetResponse(dataset);
    }

    @Override
    public EvalCaseResponse createCase(Long datasetId, CreateEvalCaseRequest request) {
        EvalDataset dataset = requireDataset(datasetId);

        if (request == null) {
            throw new BusinessException("CreateEvalCaseRequest cannot be null");
        }

        if (!StringUtils.hasText(request.getQuestion())) {
            throw new BusinessException("question cannot be blank");
        }

        EvalCase evalCase = new EvalCase();
        evalCase.setDatasetId(dataset.getId());
        evalCase.setQuestion(request.getQuestion());
        evalCase.setExpectedAnswer(request.getExpectedAnswer());
        evalCase.setExpectedChunkIds(writeJson(request.getExpectedChunkIds()));
        evalCase.setExpectedKeywords(writeJson(request.getExpectedKeywords()));
        evalCase.setDifficulty(request.getDifficulty());
        evalCase.setStatus(STATUS_NORMAL);
        evalCase.setCreatedAt(LocalDateTime.now());
        evalCase.setUpdatedAt(LocalDateTime.now());

        evalCaseMapper.insert(evalCase);

        return toCaseResponse(evalCase);
    }

    @Override
    public List<EvalCaseResponse> listCases(Long datasetId) {
        requireDataset(datasetId);

        LambdaQueryWrapper<EvalCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalCase::getDatasetId, datasetId);
        wrapper.eq(EvalCase::getStatus, STATUS_NORMAL);
        wrapper.orderByAsc(EvalCase::getId);

        List<EvalCase> cases = evalCaseMapper.selectList(wrapper);

        List<EvalCaseResponse> responses = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            responses.add(toCaseResponse(evalCase));
        }

        return responses;
    }

    @Override
    public EvalRunResponse startRun(StartEvalRunRequest request) {
        validateStartRunRequest(request);

        EvalDataset dataset = requireDataset(request.getDatasetId());

        List<EvalCase> cases = loadCases(dataset.getId());
        if (cases.isEmpty()) {
            throw new BusinessException("当前评测集中没有可执行的评测 case");
        }

        EvalRun run = createInitialRun(request, cases.size());
        evalRunMapper.insert(run);

        int successCount = 0;
        int failedCount = 0;

        double totalRecall = 0.0;
        double totalHit = 0.0;
        double totalMrr = 0.0;
        double totalAnswerKeywordHit = 0.0;
        double totalCitationCorrect = 0.0;
        double totalLatencyMs = 0.0;

        try {
            run.setStatus(RUN_STATUS_RUNNING);
            run.setStartedAt(LocalDateTime.now());
            run.setUpdatedAt(LocalDateTime.now());
            evalRunMapper.updateById(run);

            for (EvalCase evalCase : cases) {
                EvalCaseResult result = executeSingleCase(dataset, run, evalCase);

                evalCaseResultMapper.insert(result);

                if (result.getErrorMessage() == null) {
                    successCount++;

                    totalRecall += safeDouble(result.getRecallAtK());
                    totalHit += safeInt(result.getHitAtK());
                    totalMrr += safeDouble(result.getMrr());
                    totalAnswerKeywordHit += safeDouble(result.getAnswerKeywordHit());
                    totalCitationCorrect += safeInt(result.getCitationCorrect());
                    totalLatencyMs += safeLong(result.getLatencyMs());
                } else {
                    failedCount++;
                }
            }

            fillRunSummary(run,
                    successCount,
                    failedCount,
                    totalRecall,
                    totalHit,
                    totalMrr,
                    totalAnswerKeywordHit,
                    totalCitationCorrect,
                    totalLatencyMs);

            evalRunMapper.updateById(run);

            return toRunResponse(run);
        } catch (Exception e) {
            run.setStatus(RUN_STATUS_FAILED);
            run.setFailedCaseCount(cases.size());
            run.setErrorMessage(e.getMessage());
            run.setEndedAt(LocalDateTime.now());
            run.setUpdatedAt(LocalDateTime.now());
            evalRunMapper.updateById(run);

            throw new BusinessException("评测运行失败：" + e.getMessage());
        }
    }

    @Override
    public EvalRunResponse getRun(Long runId) {
        EvalRun run = evalRunMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException("评测运行不存在");
        }
        return toRunResponse(run);
    }

    @Override
    public List<EvalCaseResultResponse> listRunResults(Long runId) {
        EvalRun run = evalRunMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException("评测运行不存在");
        }

        LambdaQueryWrapper<EvalCaseResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalCaseResult::getRunId, runId);
        wrapper.orderByAsc(EvalCaseResult::getId);

        List<EvalCaseResult> results = evalCaseResultMapper.selectList(wrapper);

        List<EvalCaseResultResponse> responses = new ArrayList<>();
        for (EvalCaseResult result : results) {
            responses.add(toCaseResultResponse(result));
        }

        return responses;
    }

    private EvalCaseResult executeSingleCase(EvalDataset dataset, EvalRun run, EvalCase evalCase) {
        long startTime = System.currentTimeMillis();

        EvalCaseResult result = new EvalCaseResult();
        result.setRunId(run.getId());
        result.setCaseId(evalCase.getId());
        result.setQuestion(evalCase.getQuestion());
        result.setReferenceChunkIds(evalCase.getExpectedChunkIds());

        try {
            List<Long> expectedChunkIds = parseLongList(evalCase.getExpectedChunkIds());
            List<String> expectedKeywords = parseStringList(evalCase.getExpectedKeywords());

            SearchChunksRequest searchRequest = new SearchChunksRequest();
            searchRequest.setSpaceId(dataset.getSpaceId());
            searchRequest.setQuery(evalCase.getQuestion());
            searchRequest.setRetrievalMode(run.getRetrievalMode());
            searchRequest.setTopK(run.getTopK());
            searchRequest.setCandidateK(run.getCandidateK());
            searchRequest.setVectorWeight(run.getVectorWeight());
            searchRequest.setKeywordWeight(run.getKeywordWeight());

            List<SearchChunkResponse> retrievedChunks = ragRetrievalService.search(searchRequest);
            List<Long> retrievedChunkIds = extractChunkIds(retrievedChunks);

            String answer = null;
            if (run.getEnableAnswerGeneration() != null && run.getEnableAnswerGeneration() == 1) {
                answer = generateAnswer(evalCase.getQuestion(), retrievedChunks);
            }

            double recallAtK = calculateRecallAtK(expectedChunkIds, retrievedChunkIds);
            int hitAtK = calculateHitAtK(expectedChunkIds, retrievedChunkIds);
            double mrr = calculateMrr(expectedChunkIds, retrievedChunkIds);
            double answerKeywordHit = calculateAnswerKeywordHit(answer, expectedKeywords);
            int citationCorrect = calculateCitationCorrect(expectedChunkIds, retrievedChunkIds);
            int grounded = retrievedChunks == null || retrievedChunks.isEmpty() ? 0 : 1;

            long latencyMs = System.currentTimeMillis() - startTime;

            result.setAnswer(answer);
            result.setRetrievedChunkIds(writeJson(retrievedChunkIds));
            result.setRecallAtK(recallAtK);
            result.setHitAtK(hitAtK);
            result.setMrr(mrr);
            result.setAnswerKeywordHit(answerKeywordHit);
            result.setCitationCorrect(citationCorrect);
            result.setGrounded(grounded);
            result.setLatencyMs(latencyMs);
            result.setCreatedAt(LocalDateTime.now());
            result.setUpdatedAt(LocalDateTime.now());

            return result;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;

            result.setRecallAtK(0.0);
            result.setHitAtK(0);
            result.setMrr(0.0);
            result.setAnswerKeywordHit(0.0);
            result.setCitationCorrect(0);
            result.setGrounded(0);
            result.setLatencyMs(latencyMs);
            result.setErrorMessage(e.getMessage());
            result.setCreatedAt(LocalDateTime.now());
            result.setUpdatedAt(LocalDateTime.now());

            return result;
        }
    }

    private void validateCompareRunsRequest(CompareEvalRunsRequest request) {
        if (request == null) {
            throw new BusinessException("CompareEvalRunsRequest cannot be null");
        }

        if (request.getRunIds() == null || request.getRunIds().isEmpty()) {
            throw new BusinessException("runIds cannot be empty");
        }

        if (request.getRunIds().size() < 2) {
            throw new BusinessException("至少需要提供两个 runId 才能进行对比");
        }

        for (Long runId : request.getRunIds()) {
            if (runId == null) {
                throw new BusinessException("runId cannot be null");
            }
        }
    }

    private EvalRunCompareItemResponse toCompareItemResponse(Integer orderIndex, EvalRun run, EvalRun baselineRun) {
        double baselineRecall = safeDouble(baselineRun.getAvgRecallAtK());
        double baselineHitRate = safeDouble(baselineRun.getHitRateAtK());
        double baselineMrr = safeDouble(baselineRun.getMrr());
        double baselineLatency = safeDouble(baselineRun.getAvgLatencyMs());

        double currentRecall = safeDouble(run.getAvgRecallAtK());
        double currentHitRate = safeDouble(run.getHitRateAtK());
        double currentMrr = safeDouble(run.getMrr());
        double currentLatency = safeDouble(run.getAvgLatencyMs());

        return new EvalRunCompareItemResponse()
                .setOrderIndex(orderIndex)
                .setRunId(run.getId())
                .setDatasetId(run.getDatasetId())
                .setRunName(run.getRunName())
                .setRetrievalMode(run.getRetrievalMode())
                .setTopK(run.getTopK())
                .setCandidateK(run.getCandidateK())
                .setVectorWeight(run.getVectorWeight())
                .setKeywordWeight(run.getKeywordWeight())
                .setEnableAnswerGeneration(run.getEnableAnswerGeneration() != null && run.getEnableAnswerGeneration() == 1)
                .setStatus(run.getStatus())
                .setTotalCaseCount(run.getTotalCaseCount())
                .setSuccessCaseCount(run.getSuccessCaseCount())
                .setFailedCaseCount(run.getFailedCaseCount())
                .setAvgRecallAtK(run.getAvgRecallAtK())
                .setHitRateAtK(run.getHitRateAtK())
                .setMrr(run.getMrr())
                .setAvgAnswerKeywordHit(run.getAvgAnswerKeywordHit())
                .setCitationCorrectRate(run.getCitationCorrectRate())
                .setAvgLatencyMs(run.getAvgLatencyMs())
                .setDeltaAvgRecallAtK(currentRecall - baselineRecall)
                .setDeltaHitRateAtK(currentHitRate - baselineHitRate)
                .setDeltaMrr(currentMrr - baselineMrr)
                .setDeltaAvgLatencyMs(currentLatency - baselineLatency);
    }

    private Long findBestRecallRunId(List<EvalRunCompareItemResponse> items) {
        return items.stream()
                .max(Comparator.comparing(item -> safeDouble(item.getAvgRecallAtK())))
                .map(EvalRunCompareItemResponse::getRunId)
                .orElse(null);
    }

    private Long findBestHitRateRunId(List<EvalRunCompareItemResponse> items) {
        return items.stream()
                .max(Comparator.comparing(item -> safeDouble(item.getHitRateAtK())))
                .map(EvalRunCompareItemResponse::getRunId)
                .orElse(null);
    }

    private Long findBestMrrRunId(List<EvalRunCompareItemResponse> items) {
        return items.stream()
                .max(Comparator.comparing(item -> safeDouble(item.getMrr())))
                .map(EvalRunCompareItemResponse::getRunId)
                .orElse(null);
    }

    private Long findFastestRunId(List<EvalRunCompareItemResponse> items) {
        return items.stream()
                .filter(item -> item.getAvgLatencyMs() != null)
                .min(Comparator.comparing(EvalRunCompareItemResponse::getAvgLatencyMs))
                .map(EvalRunCompareItemResponse::getRunId)
                .orElse(null);
    }

    private String buildCompareSummary(List<EvalRunCompareItemResponse> items,
                                       Long bestRecallRunId,
                                       Long bestHitRateRunId,
                                       Long bestMrrRunId,
                                       Long fastestRunId) {
        StringBuilder builder = new StringBuilder();

        builder.append("本次共对比 ")
                .append(items.size())
                .append(" 次评测运行。");

        if (bestRecallRunId != null) {
            builder.append(" Recall@K 最优的 runId 为 ")
                    .append(bestRecallRunId)
                    .append("。");
        }

        if (bestHitRateRunId != null) {
            builder.append(" Hit@K 最优的 runId 为 ")
                    .append(bestHitRateRunId)
                    .append("。");
        }

        if (bestMrrRunId != null) {
            builder.append(" MRR 最优的 runId 为 ")
                    .append(bestMrrRunId)
                    .append("。");
        }

        if (fastestRunId != null) {
            builder.append(" 平均延迟最低的 runId 为 ")
                    .append(fastestRunId)
                    .append("。");
        }

        builder.append(" delta 字段表示相对于第一个 runId 的变化量。");

        return builder.toString();
    }

    private String generateAnswer(String question, List<SearchChunkResponse> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "当前知识库中没有检索到足够相关的内容，因此无法基于已有知识库给出可靠回答。";
        }

        String prompt = ragPromptBuilder.buildPrompt(question, chunks);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    private double calculateRecallAtK(List<Long> expectedChunkIds, List<Long> retrievedChunkIds) {
        if (expectedChunkIds == null || expectedChunkIds.isEmpty()) {
            return 0.0;
        }

        if (retrievedChunkIds == null || retrievedChunkIds.isEmpty()) {
            return 0.0;
        }

        Set<Long> expectedSet = new HashSet<>(expectedChunkIds);
        Set<Long> retrievedSet = new HashSet<>(retrievedChunkIds);

        int hitCount = 0;
        for (Long expectedId : expectedSet) {
            if (retrievedSet.contains(expectedId)) {
                hitCount++;
            }
        }

        return (double) hitCount / expectedSet.size();
    }

    private int calculateHitAtK(List<Long> expectedChunkIds, List<Long> retrievedChunkIds) {
        if (expectedChunkIds == null || expectedChunkIds.isEmpty()) {
            return 0;
        }

        if (retrievedChunkIds == null || retrievedChunkIds.isEmpty()) {
            return 0;
        }

        Set<Long> expectedSet = new HashSet<>(expectedChunkIds);

        for (Long retrievedId : retrievedChunkIds) {
            if (expectedSet.contains(retrievedId)) {
                return 1;
            }
        }

        return 0;
    }

    private double calculateMrr(List<Long> expectedChunkIds, List<Long> retrievedChunkIds) {
        if (expectedChunkIds == null || expectedChunkIds.isEmpty()) {
            return 0.0;
        }

        if (retrievedChunkIds == null || retrievedChunkIds.isEmpty()) {
            return 0.0;
        }

        Set<Long> expectedSet = new HashSet<>(expectedChunkIds);

        for (int i = 0; i < retrievedChunkIds.size(); i++) {
            Long retrievedId = retrievedChunkIds.get(i);
            if (expectedSet.contains(retrievedId)) {
                return 1.0 / (i + 1);
            }
        }

        return 0.0;
    }

    private double calculateAnswerKeywordHit(String answer, List<String> expectedKeywords) {
        if (!StringUtils.hasText(answer)) {
            return 0.0;
        }

        if (expectedKeywords == null || expectedKeywords.isEmpty()) {
            return 0.0;
        }

        int hitCount = 0;
        String lowerAnswer = answer.toLowerCase();

        for (String keyword : expectedKeywords) {
            if (StringUtils.hasText(keyword) && lowerAnswer.contains(keyword.toLowerCase())) {
                hitCount++;
            }
        }

        return (double) hitCount / expectedKeywords.size();
    }

    private int calculateCitationCorrect(List<Long> expectedChunkIds, List<Long> retrievedChunkIds) {
        return calculateHitAtK(expectedChunkIds, retrievedChunkIds);
    }

    private List<Long> extractChunkIds(List<SearchChunkResponse> chunks) {
        List<Long> ids = new ArrayList<>();

        if (chunks == null || chunks.isEmpty()) {
            return ids;
        }

        for (SearchChunkResponse chunk : chunks) {
            if (chunk.getChunkId() != null) {
                ids.add(chunk.getChunkId());
            }
        }

        return ids;
    }

    private void fillRunSummary(EvalRun run,
                                int successCount,
                                int failedCount,
                                double totalRecall,
                                double totalHit,
                                double totalMrr,
                                double totalAnswerKeywordHit,
                                double totalCitationCorrect,
                                double totalLatencyMs) {
        int totalCount = successCount + failedCount;

        run.setSuccessCaseCount(successCount);
        run.setFailedCaseCount(failedCount);
        run.setTotalCaseCount(totalCount);

        if (successCount > 0) {
            run.setAvgRecallAtK(totalRecall / successCount);
            run.setHitRateAtK(totalHit / successCount);
            run.setMrr(totalMrr / successCount);
            run.setAvgAnswerKeywordHit(totalAnswerKeywordHit / successCount);
            run.setCitationCorrectRate(totalCitationCorrect / successCount);
            run.setAvgLatencyMs(totalLatencyMs / successCount);
        } else {
            run.setAvgRecallAtK(0.0);
            run.setHitRateAtK(0.0);
            run.setMrr(0.0);
            run.setAvgAnswerKeywordHit(0.0);
            run.setCitationCorrectRate(0.0);
            run.setAvgLatencyMs(0.0);
        }

        if (failedCount == 0) {
            run.setStatus(RUN_STATUS_SUCCESS);
        } else if (successCount > 0) {
            run.setStatus(RUN_STATUS_PARTIAL_SUCCESS);
        } else {
            run.setStatus(RUN_STATUS_FAILED);
        }

        run.setEndedAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
    }

    private EvalRun createInitialRun(StartEvalRunRequest request, int totalCaseCount) {
        EvalRun run = new EvalRun();
        run.setDatasetId(request.getDatasetId());
        run.setRunName(resolveRunName(request));
        run.setRetrievalMode(resolveRetrievalMode(request));
        run.setTopK(resolveTopK(request));
        run.setCandidateK(resolveCandidateK(request));
        run.setVectorWeight(request.getVectorWeight());
        run.setKeywordWeight(request.getKeywordWeight());
        run.setEnableAnswerGeneration(Boolean.TRUE.equals(request.getEnableAnswerGeneration()) ? 1 : 0);
        run.setStatus(RUN_STATUS_PENDING);
        run.setTotalCaseCount(totalCaseCount);
        run.setSuccessCaseCount(0);
        run.setFailedCaseCount(0);
        run.setCreatedAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        return run;
    }

    private String resolveRunName(StartEvalRunRequest request) {
        if (StringUtils.hasText(request.getRunName())) {
            return request.getRunName();
        }

        return "eval-run-" + System.currentTimeMillis();
    }

    private String resolveRetrievalMode(StartEvalRunRequest request) {
        if (StringUtils.hasText(request.getRetrievalMode())) {
            return request.getRetrievalMode();
        }

        return "VECTOR_ONLY";
    }

    private int resolveTopK(StartEvalRunRequest request) {
        if (request.getTopK() == null || request.getTopK() <= 0) {
            return DEFAULT_TOP_K;
        }

        return request.getTopK();
    }

    private int resolveCandidateK(StartEvalRunRequest request) {
        if (request.getCandidateK() == null || request.getCandidateK() <= 0) {
            return DEFAULT_CANDIDATE_K;
        }

        return request.getCandidateK();
    }

    private void validateStartRunRequest(StartEvalRunRequest request) {
        if (request == null) {
            throw new BusinessException("StartEvalRunRequest cannot be null");
        }

        if (request.getDatasetId() == null) {
            throw new BusinessException("datasetId cannot be null");
        }
    }

    private EvalDataset requireDataset(Long datasetId) {
        if (datasetId == null) {
            throw new BusinessException("datasetId cannot be null");
        }

        EvalDataset dataset = evalDatasetMapper.selectById(datasetId);

        if (dataset == null || dataset.getStatus() == null || dataset.getStatus() != STATUS_NORMAL) {
            throw new BusinessException("评测数据集不存在");
        }

        return dataset;
    }

    private List<EvalCase> loadCases(Long datasetId) {
        LambdaQueryWrapper<EvalCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalCase::getDatasetId, datasetId);
        wrapper.eq(EvalCase::getStatus, STATUS_NORMAL);
        wrapper.orderByAsc(EvalCase::getId);

        return evalCaseMapper.selectList(wrapper);
    }

    private EvalDatasetResponse toDatasetResponse(EvalDataset dataset) {
        return new EvalDatasetResponse()
                .setId(dataset.getId())
                .setSpaceId(dataset.getSpaceId())
                .setName(dataset.getName())
                .setDescription(dataset.getDescription())
                .setStatus(dataset.getStatus())
                .setCreatedAt(dataset.getCreatedAt())
                .setUpdatedAt(dataset.getUpdatedAt());
    }

    private EvalCaseResponse toCaseResponse(EvalCase evalCase) {
        return new EvalCaseResponse()
                .setId(evalCase.getId())
                .setDatasetId(evalCase.getDatasetId())
                .setQuestion(evalCase.getQuestion())
                .setExpectedAnswer(evalCase.getExpectedAnswer())
                .setExpectedChunkIds(parseLongList(evalCase.getExpectedChunkIds()))
                .setExpectedKeywords(parseStringList(evalCase.getExpectedKeywords()))
                .setDifficulty(evalCase.getDifficulty())
                .setStatus(evalCase.getStatus())
                .setCreatedAt(evalCase.getCreatedAt())
                .setUpdatedAt(evalCase.getUpdatedAt());
    }

    private EvalRunResponse toRunResponse(EvalRun run) {
        return new EvalRunResponse()
                .setId(run.getId())
                .setDatasetId(run.getDatasetId())
                .setRunName(run.getRunName())
                .setRetrievalMode(run.getRetrievalMode())
                .setTopK(run.getTopK())
                .setCandidateK(run.getCandidateK())
                .setVectorWeight(run.getVectorWeight())
                .setKeywordWeight(run.getKeywordWeight())
                .setEnableAnswerGeneration(run.getEnableAnswerGeneration() != null && run.getEnableAnswerGeneration() == 1)
                .setStatus(run.getStatus())
                .setTotalCaseCount(run.getTotalCaseCount())
                .setSuccessCaseCount(run.getSuccessCaseCount())
                .setFailedCaseCount(run.getFailedCaseCount())
                .setAvgRecallAtK(run.getAvgRecallAtK())
                .setHitRateAtK(run.getHitRateAtK())
                .setMrr(run.getMrr())
                .setAvgAnswerKeywordHit(run.getAvgAnswerKeywordHit())
                .setCitationCorrectRate(run.getCitationCorrectRate())
                .setAvgLatencyMs(run.getAvgLatencyMs())
                .setErrorMessage(run.getErrorMessage())
                .setStartedAt(run.getStartedAt())
                .setEndedAt(run.getEndedAt());
    }

    private EvalCaseResultResponse toCaseResultResponse(EvalCaseResult result) {
        return new EvalCaseResultResponse()
                .setId(result.getId())
                .setRunId(result.getRunId())
                .setCaseId(result.getCaseId())
                .setQuestion(result.getQuestion())
                .setAnswer(result.getAnswer())
                .setRetrievedChunkIds(result.getRetrievedChunkIds())
                .setReferenceChunkIds(result.getReferenceChunkIds())
                .setRecallAtK(result.getRecallAtK())
                .setHitAtK(result.getHitAtK())
                .setMrr(result.getMrr())
                .setAnswerKeywordHit(result.getAnswerKeywordHit())
                .setCitationCorrect(result.getCitationCorrect())
                .setGrounded(result.getGrounded())
                .setLatencyMs(result.getLatencyMs())
                .setErrorMessage(result.getErrorMessage());
    }

    private String writeJson(Object value) {
        if (value == null) {
            return "[]";
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("JSON 序列化失败：" + e.getMessage());
        }
    }

    private List<Long> parseLongList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}