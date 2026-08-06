package com.nuaa.ragagent.service.impl;

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
import com.nuaa.ragagent.request.CompareEvalRunsRequest;
import com.nuaa.ragagent.request.CreateEvalCaseRequest;
import com.nuaa.ragagent.request.CreateEvalDatasetRequest;
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.request.StartEvalRunRequest;
import com.nuaa.ragagent.response.EvalCaseResponse;
import com.nuaa.ragagent.response.EvalDatasetResponse;
import com.nuaa.ragagent.response.EvalRunCompareResponse;
import com.nuaa.ragagent.response.EvalRunResponse;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.service.RagRetrievalService;
import com.nuaa.ragagent.util.RagPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagEvaluationServiceImplTest {

    private static final int STATUS_NORMAL = 1;
    private static final int RUN_STATUS_SUCCESS = 2;
    private static final int RUN_STATUS_PARTIAL_SUCCESS = 4;

    @Mock
    private EvalDatasetMapper datasetMapper;

    @Mock
    private EvalCaseMapper caseMapper;

    @Mock
    private EvalRunMapper runMapper;

    @Mock
    private EvalCaseResultMapper caseResultMapper;

    @Mock
    private RagRetrievalService retrievalService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private RagPromptBuilder promptBuilder;

    private RagEvaluationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RagEvaluationServiceImpl(datasetMapper, caseMapper, runMapper,
                caseResultMapper, retrievalService, chatClient, promptBuilder, new ObjectMapper());
    }

    // ---------- createDataset ----------

    @Test
    void createDataset_nullRequest_throws() {
        assertThatThrownBy(() -> service.createDataset(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void createDataset_missingSpaceId_throws() {
        CreateEvalDatasetRequest request = new CreateEvalDatasetRequest().setName("n");

        assertThatThrownBy(() -> service.createDataset(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("spaceId");
    }

    @Test
    void createDataset_blankName_throws() {
        CreateEvalDatasetRequest request = new CreateEvalDatasetRequest().setSpaceId(1L).setName(" ");

        assertThatThrownBy(() -> service.createDataset(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("name");
    }

    @Test
    void createDataset_success_returnsResponse() {
        when(datasetMapper.insert(any(EvalDataset.class))).thenAnswer(inv -> {
            inv.<EvalDataset>getArgument(0).setId(10L);
            return 1;
        });

        EvalDatasetResponse response = service.createDataset(
                new CreateEvalDatasetRequest().setSpaceId(1L).setName("评测集").setDescription("desc"));

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getSpaceId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("评测集");
        assertThat(response.getStatus()).isEqualTo(STATUS_NORMAL);
    }

    // ---------- createCase ----------

    @Test
    void createCase_datasetNotFound_throws() {
        when(datasetMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.createCase(1L, new CreateEvalCaseRequest().setQuestion("q")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("评测数据集不存在");
    }

    @Test
    void createCase_success_returnsResponse() {
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L, 10L));
        when(caseMapper.insert(any(EvalCase.class))).thenAnswer(inv -> {
            inv.<EvalCase>getArgument(0).setId(5L);
            return 1;
        });

        CreateEvalCaseRequest request = new CreateEvalCaseRequest()
                .setQuestion("问题")
                .setExpectedAnswer("答案")
                .setExpectedChunkIds(List.of(1L, 2L))
                .setExpectedKeywords(List.of("k1"))
                .setDifficulty("easy");

        EvalCaseResponse response = service.createCase(1L, request);

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getExpectedChunkIds()).containsExactly(1L, 2L);
        assertThat(response.getExpectedKeywords()).containsExactly("k1");
        assertThat(response.getQuestion()).isEqualTo("问题");
    }

    // ---------- startRun ----------

    @Test
    void startRun_missingDatasetId_throws() {
        assertThatThrownBy(() -> service.startRun(new StartEvalRunRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("datasetId");
    }

    @Test
    void startRun_noCases_throws() {
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L, 10L));
        when(caseMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.startRun(new StartEvalRunRequest().setDatasetId(1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可执行的评测 case");
    }

    @Test
    void startRun_success_aggregatesMetrics() {
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L, 10L));
        when(caseMapper.selectList(any())).thenReturn(List.of(
                evalCase(1L, "q1", "[1,2]", "[\"RAG\",\"检索\"]"),
                evalCase(2L, "q2", "[3]", "[\"向量化\"]")));
        when(retrievalService.search(any())).thenAnswer(inv -> {
            SearchChunksRequest request = inv.getArgument(0);
            if ("q1".equals(request.getQuery())) {
                return List.of(chunkResponse(1L), chunkResponse(9L));
            }
            if ("q2".equals(request.getQuery())) {
                return List.of(chunkResponse(3L));
            }
            return List.of();
        });
        when(runMapper.insert(any(EvalRun.class))).thenAnswer(inv -> {
            inv.<EvalRun>getArgument(0).setId(100L);
            return 1;
        });

        EvalRunResponse response = service.startRun(new StartEvalRunRequest().setDatasetId(1L));

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getTotalCaseCount()).isEqualTo(2);
        assertThat(response.getSuccessCaseCount()).isEqualTo(2);
        assertThat(response.getFailedCaseCount()).isZero();
        assertThat(response.getStatus()).isEqualTo(RUN_STATUS_SUCCESS);
        // case1 recall = 1/2，case2 recall = 1/1，平均 = 0.75
        assertThat(response.getAvgRecallAtK()).isCloseTo(0.75, within(1e-9));
        assertThat(response.getHitRateAtK()).isCloseTo(1.0, within(1e-9));
        assertThat(response.getMrr()).isCloseTo(1.0, within(1e-9));
        assertThat(response.getCitationCorrectRate()).isCloseTo(1.0, within(1e-9));
        verify(caseResultMapper, times(2)).insert(any(EvalCaseResult.class));
    }

    @Test
    void startRun_caseFailure_marksPartialSuccess() {
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L, 10L));
        when(caseMapper.selectList(any())).thenReturn(List.of(
                evalCase(1L, "q1", "[1,2]", "[]"),
                evalCase(2L, "q2", "[3]", "[]")));
        when(retrievalService.search(any())).thenAnswer(inv -> {
            SearchChunksRequest request = inv.getArgument(0);
            if ("q1".equals(request.getQuery())) {
                return List.of(chunkResponse(1L));
            }
            throw new RuntimeException("boom");
        });
        when(runMapper.insert(any(EvalRun.class))).thenAnswer(inv -> {
            inv.<EvalRun>getArgument(0).setId(100L);
            return 1;
        });

        EvalRunResponse response = service.startRun(new StartEvalRunRequest().setDatasetId(1L));

        assertThat(response.getSuccessCaseCount()).isEqualTo(1);
        assertThat(response.getFailedCaseCount()).isEqualTo(1);
        assertThat(response.getTotalCaseCount()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(RUN_STATUS_PARTIAL_SUCCESS);
        // case1 recall = 1/2 = 0.5，唯一成功 case 的平均值 = 0.5
        assertThat(response.getAvgRecallAtK()).isCloseTo(0.5, within(1e-9));

        ArgumentCaptor<EvalCaseResult> resultCaptor = ArgumentCaptor.forClass(EvalCaseResult.class);
        verify(caseResultMapper, times(2)).insert(resultCaptor.capture());
        assertThat(resultCaptor.getAllValues().get(0).getErrorMessage()).isNull();
        assertThat(resultCaptor.getAllValues().get(1).getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void startRun_enableAnswerGeneration_savesAnswerAndKeywordHit() {
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L, 10L));
        when(caseMapper.selectList(any())).thenReturn(List.of(
                evalCase(1L, "q1", "[1]", "[\"异步\",\"向量化\"]")));
        when(retrievalService.search(any())).thenReturn(List.of(chunkResponse(1L)));
        when(promptBuilder.buildPrompt(anyString(), anyList())).thenReturn("prompt");
        when(runMapper.insert(any(EvalRun.class))).thenAnswer(inv -> {
            inv.<EvalRun>getArgument(0).setId(100L);
            return 1;
        });

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("异步向量化任务用于管理任务状态。");

        service.startRun(new StartEvalRunRequest().setDatasetId(1L).setEnableAnswerGeneration(true));

        ArgumentCaptor<EvalCaseResult> resultCaptor = ArgumentCaptor.forClass(EvalCaseResult.class);
        verify(caseResultMapper).insert(resultCaptor.capture());
        EvalCaseResult result = resultCaptor.getValue();
        assertThat(result.getAnswer()).isEqualTo("异步向量化任务用于管理任务状态。");
        assertThat(result.getAnswerKeywordHit()).isCloseTo(1.0, within(1e-9));
        verify(chatClient).prompt();
    }

    // ---------- compareRuns ----------

    @Test
    void compareRuns_nullRequest_throws() {
        assertThatThrownBy(() -> service.compareRuns(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void compareRuns_emptyRunIds_throws() {
        assertThatThrownBy(() -> service.compareRuns(new CompareEvalRunsRequest().setRunIds(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("runIds cannot be empty");
    }

    @Test
    void compareRuns_singleRunId_throws() {
        assertThatThrownBy(() -> service.compareRuns(new CompareEvalRunsRequest().setRunIds(List.of(1L))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要提供两个 runId");
    }

    @Test
    void compareRuns_nullRunId_throws() {
        assertThatThrownBy(() -> service.compareRuns(new CompareEvalRunsRequest().setRunIds(Arrays.asList(1L, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("runId cannot be null");
    }

    @Test
    void compareRuns_partialMissingRuns_throws() {
        when(runMapper.selectByIds(any())).thenReturn(List.of(run(1L, 0.5, 0.6, 0.4, 100.0)));

        assertThatThrownBy(() -> service.compareRuns(new CompareEvalRunsRequest().setRunIds(List.of(1L, 2L))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部分评测运行不存在");
    }

    @Test
    void compareRuns_success_calculatesBestAndDeltas() {
        when(runMapper.selectByIds(any())).thenReturn(List.of(
                run(1L, 0.5, 0.6, 0.4, 100.0),
                run(2L, 0.8, 0.7, 0.5, 80.0)));

        EvalRunCompareResponse response = service.compareRuns(
                new CompareEvalRunsRequest().setRunIds(List.of(1L, 2L)));

        assertThat(response.getBaselineRunId()).isEqualTo(1L);
        assertThat(response.getComparedRunCount()).isEqualTo(2);
        assertThat(response.getBestRecallRunId()).isEqualTo(2L);
        assertThat(response.getBestHitRateRunId()).isEqualTo(2L);
        assertThat(response.getBestMrrRunId()).isEqualTo(2L);
        assertThat(response.getFastestRunId()).isEqualTo(2L);
        assertThat(response.getSummary()).contains("本次共对比 2 次评测运行");
        assertThat(response.getSummary()).contains("Recall@K 最优的 runId 为 2");

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getOrderIndex()).isEqualTo(1);
        assertThat(response.getItems().get(0).getRunId()).isEqualTo(1L);
        assertThat(response.getItems().get(0).getDeltaAvgRecallAtK()).isCloseTo(0.0, within(1e-9));
        assertThat(response.getItems().get(0).getDeltaAvgLatencyMs()).isCloseTo(0.0, within(1e-9));

        assertThat(response.getItems().get(1).getOrderIndex()).isEqualTo(2);
        assertThat(response.getItems().get(1).getRunId()).isEqualTo(2L);
        assertThat(response.getItems().get(1).getDeltaAvgRecallAtK()).isCloseTo(0.3, within(1e-9));
        assertThat(response.getItems().get(1).getDeltaHitRateAtK()).isCloseTo(0.1, within(1e-9));
        assertThat(response.getItems().get(1).getDeltaMrr()).isCloseTo(0.1, within(1e-9));
        assertThat(response.getItems().get(1).getDeltaAvgLatencyMs()).isCloseTo(-20.0, within(1e-9));
    }

    // ---------- helpers ----------

    private EvalDataset dataset(Long id, Long spaceId) {
        return new EvalDataset().setId(id).setSpaceId(spaceId).setStatus(STATUS_NORMAL);
    }

    private EvalCase evalCase(Long id, String question, String expectedChunkIds, String expectedKeywords) {
        return new EvalCase()
                .setId(id)
                .setQuestion(question)
                .setExpectedChunkIds(expectedChunkIds)
                .setExpectedKeywords(expectedKeywords)
                .setStatus(STATUS_NORMAL);
    }

    private SearchChunkResponse chunkResponse(Long chunkId) {
        SearchChunkResponse chunk = new SearchChunkResponse();
        chunk.setChunkId(chunkId);
        chunk.setContent("content-" + chunkId);
        return chunk;
    }

    private EvalRun run(Long id, double recall, double hitRate, double mrr, double latency) {
        return new EvalRun()
                .setId(id)
                .setRunName("run-" + id)
                .setRetrievalMode("HYBRID")
                .setTopK(5)
                .setCandidateK(20)
                .setEnableAnswerGeneration(0)
                .setStatus(2)
                .setTotalCaseCount(12)
                .setSuccessCaseCount(12)
                .setFailedCaseCount(0)
                .setAvgRecallAtK(recall)
                .setHitRateAtK(hitRate)
                .setMrr(mrr)
                .setAvgAnswerKeywordHit(0.5)
                .setCitationCorrectRate(0.6)
                .setAvgLatencyMs(latency);
    }
}
