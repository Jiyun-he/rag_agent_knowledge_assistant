package com.nuaa.ragagent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.entity.KbIndexTask;
import com.nuaa.ragagent.enums.TaskStatus;
import com.nuaa.ragagent.enums.TaskType;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.exception.IndexBuildAbortedException;
import com.nuaa.ragagent.mapper.KbDocumentMapper;
import com.nuaa.ragagent.mapper.KbIndexTaskMapper;
import com.nuaa.ragagent.response.IndexBuildResult;
import com.nuaa.ragagent.response.IndexTaskResponse;
import com.nuaa.ragagent.service.IndexCleanupService;
import com.nuaa.ragagent.service.KnowledgeEmbeddingService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证持久化索引任务核心逻辑：登记去重、执行分发与状态流转、指数退避、人工重试、抢占与超时回收。
 */
@ExtendWith(MockitoExtension.class)
class IndexTaskServiceImplTest {

    @Mock
    private KbIndexTaskMapper indexTaskMapper;

    @Mock
    private KbDocumentMapper documentMapper;

    @Mock
    private KnowledgeEmbeddingService knowledgeEmbeddingService;

    @Mock
    private IndexCleanupService indexCleanupService;

    private IndexTaskServiceImpl service;

    @BeforeAll
    static void initMybatisLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, KbIndexTask.class);
        TableInfoHelper.initTableInfo(assistant, KbDocument.class);
    }

    @BeforeEach
    void setUp() {
        // 重试上限 3，退避 base=1000ms / max=8000ms，超时 30min
        service = new IndexTaskServiceImpl(indexTaskMapper, documentMapper,
                knowledgeEmbeddingService, indexCleanupService, 3, 1000, 8000, 1_800_000);
    }

    // ---------- createTask ----------

    @Test
    void createTask_activeTaskExists_dedupReturnsExisting() {
        KbIndexTask existing = new KbIndexTask().setId(5L).setDocumentId(1L).setSpaceId(1L)
                .setTaskType(TaskType.BUILD_INDEX.name()).setStatus(TaskStatus.PENDING.getValue());
        when(indexTaskMapper.selectOne(any())).thenReturn(existing);

        IndexTaskResponse response = service.createTask(1L, TaskType.BUILD_INDEX);

        assertThat(response.getTaskId()).isEqualTo(5L);
        verify(indexTaskMapper, never()).insert((KbIndexTask) any());
    }

    @Test
    void createTask_noActiveTask_insertsPending() {
        KbDocument document = new KbDocument().setId(1L).setSpaceId(1L).setStatus(1);
        when(indexTaskMapper.selectOne(any())).thenReturn(null);
        when(documentMapper.selectById(1L)).thenReturn(document);
        when(indexTaskMapper.insert(any(KbIndexTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbIndexTask.class).setId(9L);
            return 1;
        });

        IndexTaskResponse response = service.createTask(1L, TaskType.BUILD_INDEX);

        assertThat(response.getTaskId()).isEqualTo(9L);
        assertThat(response.getStatus()).isEqualTo(TaskStatus.PENDING.getValue());
        assertThat(response.getSpaceId()).isEqualTo(1L);
        assertThat(response.getRetryCount()).isZero();

        ArgumentCaptor<KbIndexTask> captor = ArgumentCaptor.forClass(KbIndexTask.class);
        verify(indexTaskMapper).insert(captor.capture());
        assertThat(captor.getValue().getTaskType()).isEqualTo(TaskType.BUILD_INDEX.name());
        assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.PENDING.getValue());
    }

    @Test
    void createTask_buildOnInvalidDocument_rejects() {
        KbDocument document = new KbDocument().setId(1L).setSpaceId(1L).setStatus(0); // INVALID
        when(indexTaskMapper.selectOne(any())).thenReturn(null);
        when(documentMapper.selectById(1L)).thenReturn(document);

        assertThatThrownBy(() -> service.createTask(1L, TaskType.BUILD_INDEX))
                .isInstanceOf(BusinessException.class);
        verify(indexTaskMapper, never()).insert((KbIndexTask) any());
    }

    @Test
    void createTask_concurrentDuplicateKey_reusesExistingActiveTask() {
        // 快路径查询未命中（并发请求同时通过检查），insert 被唯一索引 uk_task_active_dedup 拦下；
        // 此时应重新查询并复用先插入的活跃任务，而不是把异常抛给调用方
        KbDocument document = new KbDocument().setId(1L).setSpaceId(1L).setStatus(1);
        KbIndexTask concurrent = new KbIndexTask().setId(7L).setDocumentId(1L).setSpaceId(1L)
                .setTaskType(TaskType.BUILD_INDEX.name()).setStatus(TaskStatus.PENDING.getValue());
        when(indexTaskMapper.selectOne(any())).thenReturn(null, concurrent);
        when(documentMapper.selectById(1L)).thenReturn(document);
        when(indexTaskMapper.insert(any(KbIndexTask.class)))
                .thenThrow(new DuplicateKeyException("uk_task_active_dedup"));

        IndexTaskResponse response = service.createTask(1L, TaskType.BUILD_INDEX);

        assertThat(response.getTaskId()).isEqualTo(7L);
    }

    @Test
    void createTask_duplicateKeyWithNoRecoverableTask_rethrows() {
        // 重复键不是本约束造成（或并发任务已结束）时，不能吞掉异常
        KbDocument document = new KbDocument().setId(1L).setSpaceId(1L).setStatus(1);
        when(indexTaskMapper.selectOne(any())).thenReturn(null);
        when(documentMapper.selectById(1L)).thenReturn(document);
        when(indexTaskMapper.insert(any(KbIndexTask.class)))
                .thenThrow(new DuplicateKeyException("other_unique_key"));

        assertThatThrownBy(() -> service.createTask(1L, TaskType.BUILD_INDEX))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void retryTask_conflictsWithExistingActiveTask_translatesToBusinessException() {
        // 重置为 PENDING 会重新占用去重名额，唯一索引冲突时应转为可读的业务异常
        KbIndexTask failed = new KbIndexTask().setId(3L).setDocumentId(1L).setSpaceId(1L)
                .setTaskType(TaskType.BUILD_INDEX.name()).setStatus(TaskStatus.FAILED.getValue());
        when(indexTaskMapper.selectById(3L)).thenReturn(failed);
        when(indexTaskMapper.update(isNull(), any())).thenThrow(new DuplicateKeyException("uk_task_active_dedup"));

        assertThatThrownBy(() -> service.retryTask(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活跃");
    }

    @Test
    void executeTask_buildAborted_documentInvalidated_marksFailedWithoutRetry() {
        // 文档在构建期间被删除/更新：结果作废，应直接置 FAILED 终态，不进入退避重试
        KbIndexTask task = task(1L, TaskType.BUILD_INDEX, TaskStatus.RUNNING, 0);
        when(knowledgeEmbeddingService.buildDocumentIndex(1L, false))
                .thenThrow(new IndexBuildAbortedException("document invalidated during index build"));

        service.executeTask(task);

        ArgumentCaptor<LambdaUpdateWrapper<KbIndexTask>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(indexTaskMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(TaskStatus.FAILED.getValue())
                .doesNotContain(TaskStatus.RETRY_WAIT.getValue());
    }

    // ---------- executeTask 状态流转 ----------

    @Test
    void executeTask_buildSuccess_marksSuccess() {
        KbIndexTask task = task(1L, TaskType.BUILD_INDEX, TaskStatus.RUNNING, 0);
        when(knowledgeEmbeddingService.buildDocumentIndex(1L, false))
                .thenReturn(new IndexBuildResult().setTotalChunkCount(2).setPendingChunkCount(2)
                        .setSuccessCount(2).setFailedCount(0).setSkippedCount(0));

        service.executeTask(task);

        ArgumentCaptor<LambdaUpdateWrapper<KbIndexTask>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(indexTaskMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains(TaskStatus.SUCCESS.getValue());
    }

    @Test
    void executeTask_buildPartialFailure_retryWaitWithBackoff() {
        KbIndexTask task = task(1L, TaskType.BUILD_INDEX, TaskStatus.RUNNING, 0);
        when(knowledgeEmbeddingService.buildDocumentIndex(1L, false))
                .thenReturn(new IndexBuildResult().setTotalChunkCount(2).setPendingChunkCount(2)
                        .setSuccessCount(1).setFailedCount(1).setSkippedCount(0));

        service.executeTask(task);

        ArgumentCaptor<LambdaUpdateWrapper<KbIndexTask>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(indexTaskMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<KbIndexTask> wrapper = captor.getValue();
        assertThat(wrapper.getParamNameValuePairs().values()).contains(TaskStatus.RETRY_WAIT.getValue());
        // 退避：第 1 次重试 = base 1000ms
        assertThat(wrapper.getParamNameValuePairs().values()).contains(1); // retryCount=1
    }

    @Test
    void executeTask_buildFailureRetriesExhausted_marksFailed() {
        // retryCount=3 == maxRetryTimes，再一次失败即 FAILED
        KbIndexTask task = task(1L, TaskType.BUILD_INDEX, TaskStatus.RUNNING, 3);
        org.mockito.Mockito.doThrow(new RuntimeException("es down"))
                .when(knowledgeEmbeddingService).buildDocumentIndex(1L, false);

        service.executeTask(task);

        ArgumentCaptor<LambdaUpdateWrapper<KbIndexTask>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(indexTaskMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<KbIndexTask> wrapper = captor.getValue();
        assertThat(wrapper.getParamNameValuePairs().values()).contains(TaskStatus.FAILED.getValue());
        assertThat(wrapper.getParamNameValuePairs().values()).contains(4); // retryCount=4
    }

    @Test
    void executeTask_deleteSuccess_marksSuccessAndCleans() {
        KbIndexTask task = task(1L, TaskType.DELETE_INDEX, TaskStatus.RUNNING, 0);

        service.executeTask(task);

        verify(indexCleanupService).cleanupDocumentIndexes(1L);
        ArgumentCaptor<LambdaUpdateWrapper<KbIndexTask>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(indexTaskMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains(TaskStatus.SUCCESS.getValue());
    }

    // ---------- tryAcquire / findRunnable / recover ----------

    @Test
    void tryAcquire_updateAffectedOne_returnsTrue() {
        KbIndexTask task = task(1L, TaskType.BUILD_INDEX, TaskStatus.PENDING, 0);
        when(indexTaskMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        boolean acquired = service.tryAcquire(task);

        assertThat(acquired).isTrue();
        ArgumentCaptor<LambdaUpdateWrapper<KbIndexTask>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(indexTaskMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains(TaskStatus.RUNNING.getValue());
    }

    @Test
    void tryAcquire_updateAffectedZero_returnsFalse() {
        KbIndexTask task = task(1L, TaskType.BUILD_INDEX, TaskStatus.PENDING, 0);
        when(indexTaskMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThat(service.tryAcquire(task)).isFalse();
    }

    @Test
    void findRunnableTasks_queriesPendingOrDueRetryWait() {
        KbIndexTask pending = task(1L, TaskType.BUILD_INDEX, TaskStatus.PENDING, 0);
        when(indexTaskMapper.selectList(any())).thenReturn(List.of(pending));

        List<KbIndexTask> tasks = service.findRunnableTasks(10);

        assertThat(tasks).hasSize(1);
        ArgumentCaptor<LambdaQueryWrapper<KbIndexTask>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(indexTaskMapper).selectList(captor.capture());
        // 查询条件必须限制 PENDING 或到期 RETRY_WAIT（包含 LIMIT）
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("status");
        assertThat(sql).contains("next_retry_at");
        assertThat(sql).contains("LIMIT 10");
    }

    @Test
    void recoverRunningTasks_updatesTimedOutRunningToPending() {
        when(indexTaskMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(2);

        service.recoverRunningTasks();

        ArgumentCaptor<LambdaUpdateWrapper<KbIndexTask>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(indexTaskMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<KbIndexTask> wrapper = captor.getValue();
        // 置回 PENDING + 重试计数自增
        assertThat(wrapper.getParamNameValuePairs().values()).contains(TaskStatus.PENDING.getValue());
        assertThat(wrapper.getSqlSet()).contains("retry_count = retry_count + 1");
        // WHERE 限定 RUNNING 且 started_at 超时
        assertThat(wrapper.getSqlSegment()).contains("status");
        assertThat(wrapper.getSqlSegment()).contains("started_at");
    }

    // ---------- 人工重试 ----------

    @Test
    void retryTask_failedTask_resetToPending() {
        KbIndexTask failed = task(1L, TaskType.BUILD_INDEX, TaskStatus.FAILED, 4)
                .setErrorMessage("boom").setFinishedAt(LocalDateTime.now());
        KbIndexTask reset = task(1L, TaskType.BUILD_INDEX, TaskStatus.PENDING, 0);
        when(indexTaskMapper.selectById(1L)).thenReturn(failed, reset);
        when(indexTaskMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        IndexTaskResponse response = service.retryTask(1L);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.PENDING.getValue());
        assertThat(response.getRetryCount()).isZero();
        verify(indexTaskMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void retryTask_nonFailedTask_rejects() {
        KbIndexTask running = task(1L, TaskType.BUILD_INDEX, TaskStatus.RUNNING, 0);
        when(indexTaskMapper.selectById(1L)).thenReturn(running);

        assertThatThrownBy(() -> service.retryTask(1L))
                .isInstanceOf(BusinessException.class);
        verify(indexTaskMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    // ---------- helpers ----------

    private KbIndexTask task(Long id, TaskType type, TaskStatus status, int retryCount) {
        return new KbIndexTask()
                .setId(id)
                .setDocumentId(1L)
                .setSpaceId(1L)
                .setTaskType(type.name())
                .setStatus(status.getValue())
                .setRetryCount(retryCount)
                .setTotalChunkCount(0).setPendingChunkCount(0)
                .setSuccessCount(0).setFailedCount(0).setSkippedCount(0);
    }
}
