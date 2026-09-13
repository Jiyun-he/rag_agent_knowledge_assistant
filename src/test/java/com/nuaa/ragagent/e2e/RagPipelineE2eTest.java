package com.nuaa.ragagent.e2e;

import com.nuaa.ragagent.common.ApiResponse;
import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.entity.KbSpace;
import com.nuaa.ragagent.request.CompareEvalRunsRequest;
import com.nuaa.ragagent.request.CreateDocumentRequest;
import com.nuaa.ragagent.request.CreateEvalCaseRequest;
import com.nuaa.ragagent.request.CreateEvalDatasetRequest;
import com.nuaa.ragagent.request.CreateSpaceRequest;
import com.nuaa.ragagent.request.StartEvalRunRequest;
import com.nuaa.ragagent.response.EvalCaseResponse;
import com.nuaa.ragagent.response.EvalDatasetResponse;
import com.nuaa.ragagent.response.EvalRunCompareResponse;
import com.nuaa.ragagent.response.EvalRunResponse;
import com.nuaa.ragagent.response.IndexTaskResponse;
import com.nuaa.ragagent.service.KeywordIndexService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端测试：文档入库 → 分块 → 异步索引构建（BUILD_INDEX 任务）→ 构建评测集 → 四种检索模式评测 → 对比。
 *
 * <p>复用 docker-compose 服务（MySQL/Qdrant/ES/TEI），用 {@link TestAiConfiguration} 的
 * fake EmbeddingModel/ChatModel 替代真实 OpenAI 调用。测试用独立空间名，结束后物理清理
 * 数据库表与 ES 索引，尽量不污染开发数据。</p>
 *
 * <p>前置条件：docker-compose 中的 mysql(3306)、qdrant(6334)、elasticsearch(9200)、
 * reranker(8081) 均在运行。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestAiConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RagPipelineE2eTest {

    private static final int TASK_STATUS_SUCCESS = 3;

    private static final int TASK_STATUS_FAILED = 4;

    private static final int EMBEDDING_STATUS_DONE = 1;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KeywordIndexService keywordIndexService;

    private String baseUrl;

    // 测试文档内容（文本切分参数 maxSize=500 / overlap=80 来自 application.yml）
    private static final String DOC1_CONTENT =
            "持久化索引任务用于管理文档索引构建过程，记录任务状态、成功数量、失败数量、开始时间和结束时间。"
                    + "IndexTaskService 负责登记任务并由 worker 异步执行，完成后更新 kb_index_task 表。";

    private static final String DOC2_CONTENT =
            "关键词检索基于 Elasticsearch 的 smartcn 中文分词实现，适合类名、方法名、接口路径等精确匹配场景。"
                    + "KeywordIndexServiceImpl 提供关键词索引的写入与检索，RagRetrievalServiceImpl 在 KEYWORD_ONLY 模式下调用关键词检索。";

    /** 长文档：>500 字符，验证多 chunk 切分与 overlap；内容为评测模块主题，供评测 case 使用 */
    private static final String DOC3_CONTENT =
            "RAG 评测模块用于对不同检索策略进行量化评估，支持的指标包括 Recall@K、Hit@K、MRR、Answer Keyword Hit 和 Citation Correct Rate。"
                    .repeat(9) + "评测用于比较不同检索策略的效果。";

    private Long spaceId;
    private final List<Long> docIds = new ArrayList<>();
    private final List<Long> doc1ChunkIds = new ArrayList<>();
    private final List<Long> doc2ChunkIds = new ArrayList<>();
    private final List<Long> doc3ChunkIds = new ArrayList<>();
    private Long datasetId;
    private final List<Long> runIds = new ArrayList<>();

    // ---------- 阶段 0：环境准备 ----------

    @BeforeAll
    void initBaseUrl() {
        baseUrl = "http://localhost:" + port;
    }

    // ---------- 阶段 1：健康检查 + 建空间 ----------

    @Test
    @Order(1)
    void healthAndSetupSpace() {
        ApiResponse<Object> pong = get("/api/health/ping", new ParameterizedTypeReference<>() {
        });
        assertThat(pong.getCode()).isZero();

        String spaceName = "itest-" + System.currentTimeMillis();
        ApiResponse<KbSpace> resp = post("/api/spaces",
                new CreateSpaceRequest().setName(spaceName).setDescription("e2e pipeline test"),
                new ParameterizedTypeReference<>() {
                });
        assertThat(resp.getCode()).isZero();
        spaceId = resp.getData().getId();
        assertThat(spaceId).isNotNull();
    }

    // ---------- 阶段 2：文档入库 ----------

    @Test
    @Order(2)
    void importDocuments() {
        docIds.clear();
        docIds.add(createDocument("e2e-doc1", DOC1_CONTENT));
        docIds.add(createDocument("e2e-doc2", DOC2_CONTENT));
        docIds.add(createDocument("e2e-doc3", DOC3_CONTENT));
        assertThat(docIds).hasSize(3);
    }

    private Long createDocument(String title, String content) {
        ApiResponse<KbDocument> resp = post("/api/documents",
                new CreateDocumentRequest()
                        .setSpaceId(spaceId)
                        .setTitle(title)
                        .setContent(content)
                        .setSourceType("E2E_TEST")
                        .setSourceUri("e2e/" + title),
                new ParameterizedTypeReference<>() {
                });
        assertThat(resp.getCode()).isZero();
        assertThat(resp.getData().getId()).isNotNull();
        return resp.getData().getId();
    }

    // ---------- 阶段 3：等待自动登记的 BUILD_INDEX 任务完成 + 收集索引化 chunk ----------
    // 文档创建后事务提交即自动登记 BUILD_INDEX 任务，由 worker 异步执行，这里轮询等待其成功。

    @Test
    @Order(3)
    void waitIndexAndCollectChunks() {
        for (Long docId : docIds) {
            waitForBuildTaskSuccess(docId);
        }
        doc1ChunkIds.addAll(collectIndexedChunks(docIds.get(0), 1, DOC1_CONTENT));
        doc2ChunkIds.addAll(collectIndexedChunks(docIds.get(1), 1, DOC2_CONTENT));
        doc3ChunkIds.addAll(collectIndexedChunks(docIds.get(2), 2, DOC3_CONTENT));
    }

    private void waitForBuildTaskSuccess(Long docId) {
        long deadline = System.currentTimeMillis() + 120_000;
        int delayMs = 500;
        while (System.currentTimeMillis() < deadline) {
            ApiResponse<List<IndexTaskResponse>> resp = get("/api/rag/documents/" + docId + "/index-tasks",
                    new ParameterizedTypeReference<>() {
                    });
            assertThat(resp.getCode()).isZero();
            List<IndexTaskResponse> tasks = resp.getData();
            if (tasks == null || tasks.isEmpty()) {
                sleep(delayMs);
                delayMs = Math.min(delayMs * 2, 4000);
                continue;
            }
            IndexTaskResponse task = tasks.get(0);
            int status = task.getStatus();
            if (status == TASK_STATUS_SUCCESS) {
                return;
            }
            if (status == TASK_STATUS_FAILED) {
                throw new AssertionError("索引任务失败: " + task.getErrorMessage());
            }
            sleep(delayMs);
            delayMs = Math.min(delayMs * 2, 4000);
        }
        throw new AssertionError("等待索引任务超时: documentId=" + docId);
    }

    private List<Long> collectIndexedChunks(Long docId, int expectedCount, String fullContent) {
        ApiResponse<List<KbChunk>> resp = get("/api/documents/" + docId + "/chunks",
                new ParameterizedTypeReference<>() {
                });
        assertThat(resp.getCode()).isZero();
        List<KbChunk> chunks = resp.getData();
        assertThat(chunks).hasSize(expectedCount);

        List<Long> chunkIds = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KbChunk chunk = chunks.get(i);
            assertThat(chunk.getChunkIndex()).isEqualTo(i);
            assertThat(chunk.getContent()).isEqualTo(
                    fullContent.substring(i * 420, Math.min(i * 420 + 500, fullContent.length())));
            assertThat(chunk.getCharCount()).isEqualTo(chunk.getContent().length());
            assertThat(chunk.getEmbeddingStatus()).isEqualTo(EMBEDDING_STATUS_DONE);
            assertThat(chunk.getVectorId()).isNotNull();
            chunkIds.add(chunk.getId());
        }
        return chunkIds;
    }

    // ---------- 阶段 5：构建评测集与评测 case ----------

    @Test
    @Order(5)
    void buildEvalDatasetAndCases() {
        ApiResponse<EvalDatasetResponse> datasetResp = post("/api/rag/eval/datasets",
                new CreateEvalDatasetRequest()
                        .setSpaceId(spaceId)
                        .setName("itest-eval-" + System.currentTimeMillis())
                        .setDescription("e2e evaluation dataset"),
                new ParameterizedTypeReference<>() {
                });
        assertThat(datasetResp.getCode()).isZero();
        datasetId = datasetResp.getData().getId();
        assertThat(datasetId).isNotNull();

        createCase(datasetId, "异步向量化任务的作用是什么？",
                "异步向量化任务用于管理文档向量化过程。",
                doc1ChunkIds, List.of("异步向量化", "向量化"), "easy");
        createCase(datasetId, "关键词检索基于什么技术实现？",
                "基于 Elasticsearch 的 smartcn 中文分词。",
                doc2ChunkIds, List.of("Elasticsearch", "关键词"), "medium");
        createCase(datasetId, "RAG 评测支持哪些指标？",
                "包括 Recall、Hit、MRR 等。",
                doc3ChunkIds.subList(0, 1), List.of("Recall", "评测"), "easy");
    }

    private void createCase(Long datasetId, String question, String expectedAnswer,
                            List<Long> expectedChunkIds, List<String> expectedKeywords, String difficulty) {
        ApiResponse<EvalCaseResponse> resp = post("/api/rag/eval/datasets/" + datasetId + "/cases",
                new CreateEvalCaseRequest()
                        .setQuestion(question)
                        .setExpectedAnswer(expectedAnswer)
                        .setExpectedChunkIds(expectedChunkIds)
                        .setExpectedKeywords(expectedKeywords)
                        .setDifficulty(difficulty),
                new ParameterizedTypeReference<>() {
                });
        assertThat(resp.getCode()).isZero();
    }

    // ---------- 阶段 6：启动 4 种检索模式的评测 run ----------

    @Test
    @Order(6)
    void startFourRuns() {
        runIds.clear();
        runIds.add(startRun("itest-vector-only", "VECTOR_ONLY", false));
        runIds.add(startRun("itest-keyword-only", "KEYWORD_ONLY", false));
        runIds.add(startRun("itest-hybrid", "HYBRID", false));
        runIds.add(startRun("itest-hybrid-rerank", "HYBRID_RERANK", true));
        assertThat(runIds).hasSize(4);
    }

    private Long startRun(String runName, String retrievalMode, boolean enableAnswerGeneration) {
        ApiResponse<EvalRunResponse> resp = post("/api/rag/eval/runs",
                new StartEvalRunRequest()
                        .setDatasetId(datasetId)
                        .setRunName(runName)
                        .setRetrievalMode(retrievalMode)
                        .setTopK(5)
                        .setCandidateK(20)
                        .setEnableAnswerGeneration(enableAnswerGeneration),
                new ParameterizedTypeReference<>() {
                });
        assertThat(resp.getCode()).isZero();
        assertThat(resp.getData().getId()).isNotNull();
        return resp.getData().getId();
    }

    // ---------- 阶段 7：断言各 run 的评测指标 ----------

    @Test
    @Order(7)
    void assertRunMetrics() {
        for (int i = 0; i < runIds.size(); i++) {
            EvalRunResponse run = getRun(runIds.get(i));
            assertThat(run.getStatus()).as("run=%s", run.getRunName()).isEqualTo(2);
            assertThat(run.getTotalCaseCount()).isEqualTo(3);
            assertThat(run.getSuccessCaseCount()).isEqualTo(3);
            assertThat(run.getFailedCaseCount()).isZero();
            assertThat(run.getAvgRecallAtK()).isNotNull().isGreaterThan(0.0);
            assertThat(run.getHitRateAtK()).isNotNull().isGreaterThan(0.0);
        }
        // 未开启回答生成的 run：回答类指标不适用，应为 null（而不是 0）
        EvalRunResponse noAnswerRun = getRun(runIds.get(0));
        assertThat(noAnswerRun.getAvgAnswerKeywordHit()).isNull();
        assertThat(noAnswerRun.getAvgCitationPrecision()).isNull();
        assertThat(noAnswerRun.getGroundedRate()).isNull();

        // 最后一个 run 开启了回答生成，应产出 Answer Keyword Hit 与引用类指标
        EvalRunResponse lastRun = getRun(runIds.get(3));
        assertThat(lastRun.getAvgAnswerKeywordHit()).isNotNull().isGreaterThan(0.0);
        assertThat(lastRun.getAvgCitationPrecision()).isNotNull();
        assertThat(lastRun.getAvgCitationRecall()).isNotNull();
        assertThat(lastRun.getGroundedRate()).isNotNull();
    }

    private EvalRunResponse getRun(Long runId) {
        ApiResponse<EvalRunResponse> resp = get("/api/rag/eval/runs/" + runId,
                new ParameterizedTypeReference<>() {
                });
        assertThat(resp.getCode()).isZero();
        return resp.getData();
    }

    // ---------- 阶段 8：横向对比 4 个 run ----------

    @Test
    @Order(8)
    void compareRuns() {
        ApiResponse<EvalRunCompareResponse> resp = post("/api/rag/eval/runs/compare",
                new CompareEvalRunsRequest().setRunIds(runIds),
                new ParameterizedTypeReference<>() {
                });
        assertThat(resp.getCode()).isZero();

        EvalRunCompareResponse compare = resp.getData();
        assertThat(compare.getComparedRunCount()).isEqualTo(4);
        assertThat(compare.getItems()).hasSize(4);
        assertThat(compare.getBaselineRunId()).isEqualTo(runIds.get(0));
        assertThat(compare.getBestRecallRunId()).isNotNull();
        assertThat(compare.getBestHitRateRunId()).isNotNull();
        assertThat(compare.getBestMrrRunId()).isNotNull();
        assertThat(compare.getFastestRunId()).isNotNull();
        assertThat(compare.getSummary()).contains("本次共对比 4 次评测运行");
    }

    // ---------- 阶段 9：清理（物理删除 MySQL + 清 ES 索引） ----------

    @AfterAll
    void cleanup() {
        if (spaceId == null) {
            return;
        }
        for (Long docId : docIds) {
            try {
                keywordIndexService.deleteByDocumentId(docId);
            } catch (Exception ignored) {
                // ES 不可用时不影响数据库清理
            }
        }
        jdbcTemplate.update("DELETE FROM eval_case_result WHERE run_id IN (SELECT id FROM eval_run WHERE dataset_id IN (SELECT id FROM eval_dataset WHERE space_id = ?))", spaceId);
        jdbcTemplate.update("DELETE FROM eval_run WHERE dataset_id IN (SELECT id FROM eval_dataset WHERE space_id = ?)", spaceId);
        jdbcTemplate.update("DELETE FROM eval_case WHERE dataset_id IN (SELECT id FROM eval_dataset WHERE space_id = ?)", spaceId);
        jdbcTemplate.update("DELETE FROM eval_dataset WHERE space_id = ?", spaceId);
        jdbcTemplate.update("DELETE FROM kb_index_task WHERE space_id = ?", spaceId);
        jdbcTemplate.update("DELETE FROM qa_reference WHERE session_id IN (SELECT id FROM qa_session WHERE space_id = ?)", spaceId);
        jdbcTemplate.update("DELETE FROM qa_message WHERE session_id IN (SELECT id FROM qa_session WHERE space_id = ?)", spaceId);
        jdbcTemplate.update("DELETE FROM qa_session WHERE space_id = ?", spaceId);
        jdbcTemplate.update("DELETE FROM kb_chunk WHERE space_id = ?", spaceId);
        jdbcTemplate.update("DELETE FROM kb_document WHERE space_id = ?", spaceId);
        jdbcTemplate.update("DELETE FROM kb_space WHERE id = ?", spaceId);
    }

    // ---------- helpers ----------

    private <T> ApiResponse<T> get(String path, ParameterizedTypeReference<ApiResponse<T>> typeRef) {
        ResponseEntity<ApiResponse<T>> entity = restTemplate.exchange(baseUrl + path, HttpMethod.GET, null, typeRef);
        return entity.getBody();
    }

    private <T> ApiResponse<T> post(String path, Object body, ParameterizedTypeReference<ApiResponse<T>> typeRef) {
        HttpEntity<Object> request = new HttpEntity<>(body);
        ResponseEntity<ApiResponse<T>> entity = restTemplate.exchange(baseUrl + path, HttpMethod.POST, request, typeRef);
        return entity.getBody();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("轮询被中断", e);
        }
    }
}
