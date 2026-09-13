package com.nuaa.ragagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.entity.KbIndexTask;
import com.nuaa.ragagent.enums.DocumentStatus;
import com.nuaa.ragagent.enums.TaskStatus;
import com.nuaa.ragagent.enums.TaskType;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.exception.IndexBuildAbortedException;
import com.nuaa.ragagent.mapper.KbDocumentMapper;
import com.nuaa.ragagent.mapper.KbIndexTaskMapper;
import com.nuaa.ragagent.response.IndexBuildResult;
import com.nuaa.ragagent.response.IndexTaskResponse;
import com.nuaa.ragagent.service.IndexCleanupService;
import com.nuaa.ragagent.service.IndexTaskService;
import com.nuaa.ragagent.service.KnowledgeEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
/**
 * @author jiyunhe
 */

@Service
public class IndexTaskServiceImpl implements IndexTaskService {

    private static final Logger log = LoggerFactory.getLogger(IndexTaskServiceImpl.class);

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final KbIndexTaskMapper indexTaskMapper;

    private final KbDocumentMapper documentMapper;

    private final KnowledgeEmbeddingService knowledgeEmbeddingService;

    private final IndexCleanupService indexCleanupService;

    private final int maxRetryTimes;

    private final long retryBaseDelayMs;

    private final long retryMaxDelayMs;

    private final long runningTimeoutMs;

    public IndexTaskServiceImpl(KbIndexTaskMapper indexTaskMapper,
                                KbDocumentMapper documentMapper,
                                KnowledgeEmbeddingService knowledgeEmbeddingService,
                                IndexCleanupService indexCleanupService,
                                @Value("${rag.task.max-retry-times:5}") int maxRetryTimes,
                                @Value("${rag.task.retry-base-delay-ms:1000}") long retryBaseDelayMs,
                                @Value("${rag.task.retry-max-delay-ms:60000}") long retryMaxDelayMs,
                                @Value("${rag.task.running-timeout-ms:1800000}") long runningTimeoutMs) {
        this.indexTaskMapper = indexTaskMapper;
        this.documentMapper = documentMapper;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
        this.indexCleanupService = indexCleanupService;
        this.maxRetryTimes = maxRetryTimes;
        this.retryBaseDelayMs = retryBaseDelayMs;
        this.retryMaxDelayMs = retryMaxDelayMs;
        this.runningTimeoutMs = runningTimeoutMs;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IndexTaskResponse createTask(Long documentId, TaskType taskType) {
        if (documentId == null) {
            throw new BusinessException("documentId must not be null");
        }
        if (taskType == null) {
            throw new BusinessException("taskType must not be null");
        }

        // 去重（快路径）：同一 Document + 同一去重分组存在活跃任务（PENDING/RUNNING/RETRY_WAIT）时直接复用。
        // 注意分组口径：BUILD_INDEX 与 REPAIR_INDEX 同组，避免同一文档被并发写索引。
        String dedupGroup = taskType.dedupGroup();
        KbIndexTask existing = findActiveTask(documentId, dedupGroup);
        if (existing != null) {
            return toResponse(existing);
        }

        KbDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException("Document not found: " + documentId);
        }
        // BUILD/REPAIR 不能作用于已失效文档
        if ((taskType == TaskType.BUILD_INDEX || taskType == TaskType.REPAIR_INDEX)
                && DocumentStatus.INVALID.getValue() == document.getStatus()) {
            throw new BusinessException("document is invalid, cannot register " + taskType.name());
        }

        KbIndexTask task = new KbIndexTask()
                .setDocumentId(documentId)
                .setSpaceId(document.getSpaceId())
                .setTaskType(taskType.name())
                .setStatus(TaskStatus.PENDING.getValue())
                .setRetryCount(0)
                .setTotalChunkCount(0)
                .setPendingChunkCount(0)
                .setSuccessCount(0)
                .setFailedCount(0)
                .setSkippedCount(0)
                .setNextRetryAt(null)
                .setErrorMessage(null)
                .setStartedAt(null)
                .setFinishedAt(null);

        try {
            indexTaskMapper.insert(task);
        } catch (DuplicateKeyException e) {
            // 并发兜底：两个请求同时通过了上面的快路径检查，由唯一索引 uk_task_active_dedup 拦下后到者。
            // MySQL 的重复键错误只让该条语句失败、不会中止整个事务（与 PostgreSQL 不同），
            // 因此这里可以安全地继续查询并复用先插入的活跃任务。
            KbIndexTask concurrent = findActiveTask(documentId, dedupGroup);
            if (concurrent != null) {
                log.info("索引任务并发登记，复用已有活跃任务: type={} documentId={} taskId={}",
                        taskType.name(), documentId, concurrent.getId());
                return toResponse(concurrent);
            }
            throw e;
        }
        log.info("登记索引任务: type={} documentId={} taskId={}", taskType.name(), documentId, task.getId());
        return toResponse(task);
    }

    /**
     * 查询指定文档 + 去重分组下的活跃任务，取最早登记的一条。
     * <p>按生成列 {@code dedup_group} 过滤，与唯一索引 {@code uk_task_active_dedup} 口径一致。</p>
     */
    private KbIndexTask findActiveTask(Long documentId, String dedupGroup) {
        return indexTaskMapper.selectOne(new LambdaQueryWrapper<KbIndexTask>()
                .eq(KbIndexTask::getDocumentId, documentId)
                .apply("dedup_group = {0}", dedupGroup)
                .in(KbIndexTask::getStatus,
                        TaskStatus.PENDING.getValue(), TaskStatus.RUNNING.getValue(), TaskStatus.RETRY_WAIT.getValue())
                .orderByAsc(KbIndexTask::getId)
                .last("LIMIT 1"));
    }

    @Override
    public IndexTaskResponse getTask(Long taskId) {
        if (taskId == null) {
            throw new BusinessException("taskId must not be null");
        }
        KbIndexTask task = indexTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Index task not found: " + taskId);
        }
        return toResponse(task);
    }

    @Override
    public List<IndexTaskResponse> listDocumentTasks(Long documentId) {
        if (documentId == null) {
            throw new BusinessException("documentId must not be null");
        }
        return indexTaskMapper.selectList(new LambdaQueryWrapper<KbIndexTask>()
                        .eq(KbIndexTask::getDocumentId, documentId)
                        .orderByDesc(KbIndexTask::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IndexTaskResponse retryTask(Long taskId) {
        if (taskId == null) {
            throw new BusinessException("taskId must not be null");
        }
        KbIndexTask task = indexTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Index task not found: " + taskId);
        }
        if (!Integer.valueOf(TaskStatus.FAILED.getValue()).equals(task.getStatus())) {
            throw new BusinessException("Only FAILED task can be retried, current status: "
                    + toStatusText(task.getStatus()));
        }

        try {
            indexTaskMapper.update(null, new LambdaUpdateWrapper<KbIndexTask>()
                    .eq(KbIndexTask::getId, taskId)
                    .set(KbIndexTask::getStatus, TaskStatus.PENDING.getValue())
                    .set(KbIndexTask::getRetryCount, 0)
                    .set(KbIndexTask::getNextRetryAt, null)
                    .set(KbIndexTask::getErrorMessage, null)
                    .set(KbIndexTask::getStartedAt, null)
                    .set(KbIndexTask::getFinishedAt, null));
        } catch (DuplicateKeyException e) {
            // 重置为 PENDING 会重新占用活跃名额（active_flag=1）。若同分组下已有活跃任务，
            // 唯一索引 uk_task_active_dedup 会拦下这次 UPDATE——这正是“同组至多一个活跃任务”
            // 的不变式在人工重试路径上也生效，属于预期行为，转为可读的业务异常。
            throw new BusinessException("该文档已存在活跃的 " + task.getTaskType()
                    + " 任务，无法重试当前任务");
        }
        log.info("人工重试索引任务: taskId={}", taskId);
        return toResponse(indexTaskMapper.selectById(taskId));
    }

    @Override
    public List<KbIndexTask> findRunnableTasks(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return indexTaskMapper.selectList(new LambdaQueryWrapper<KbIndexTask>()
                .and(w -> w.eq(KbIndexTask::getStatus, TaskStatus.PENDING.getValue())
                        .or(w2 -> w2.eq(KbIndexTask::getStatus, TaskStatus.RETRY_WAIT.getValue())
                                .le(KbIndexTask::getNextRetryAt, now)))
                .orderByAsc(KbIndexTask::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    @Override
    public boolean tryAcquire(KbIndexTask task) {
        LocalDateTime now = LocalDateTime.now();
        int affected = indexTaskMapper.update(null, new LambdaUpdateWrapper<KbIndexTask>()
                .eq(KbIndexTask::getId, task.getId())
                .in(KbIndexTask::getStatus, TaskStatus.PENDING.getValue(), TaskStatus.RETRY_WAIT.getValue())
                .set(KbIndexTask::getStatus, TaskStatus.RUNNING.getValue())
                .set(KbIndexTask::getStartedAt, now)
                .set(KbIndexTask::getNextRetryAt, null)
                .set(KbIndexTask::getErrorMessage, null));
        return affected > 0;
    }

    @Async("indexTaskExecutor")
    @Override
    public void executeTask(KbIndexTask task) {
        try {
            TaskType type = TaskType.of(task.getTaskType());
            if (type == null) {
                throw new BusinessException("unknown task type: " + task.getTaskType());
            }

            switch (type) {
                case BUILD_INDEX -> completeBuild(task,
                        knowledgeEmbeddingService.buildDocumentIndex(task.getDocumentId(), false));
                case REPAIR_INDEX -> completeBuild(task,
                        knowledgeEmbeddingService.buildDocumentIndex(task.getDocumentId(), true));
                case DELETE_INDEX -> {
                    indexCleanupService.cleanupDocumentIndexes(task.getDocumentId());
                    finishSuccess(task, null, null, null, null, null);
                }
            }
        } catch (IndexBuildAbortedException e) {
            // 文档在构建期间被删除/更新：本次结果已作废，重试没有意义（构建入口会拒绝 INVALID 文档），
            // 直接置 FAILED 终态，避免按指数退避空跑 maxRetryTimes 次
            markAborted(task, e);
        } catch (Exception e) {
            recordFailure(task, e);
        }
    }

    @Override
    public void recoverRunningTasks() {
        LocalDateTime deadline = LocalDateTime.now().minus(runningTimeoutMs, ChronoUnit.MILLIS);
        // TODO(known-issue): retry_count 计数会漂移 —— 这里用 SQL 自增，而 recordFailure 用 task 快照的
        //  retryCount + 1 覆盖写。任务被回收后再次失败时，自增的这一次会被快照值覆盖，导致退避次数与实际
        //  失败次数不符（不会死循环，但重试上限语义不准）。修复方向：recordFailure 也改为 SQL 自增后再回读，
        //  或统一以数据库重读的 retry_count 为准。
        int affected = indexTaskMapper.update(null, new LambdaUpdateWrapper<KbIndexTask>()
                .eq(KbIndexTask::getStatus, TaskStatus.RUNNING.getValue())
                .lt(KbIndexTask::getStartedAt, deadline)
                .set(KbIndexTask::getStatus, TaskStatus.PENDING.getValue())
                .setSql("retry_count = retry_count + 1")
                .set(KbIndexTask::getNextRetryAt, null)
                .set(KbIndexTask::getErrorMessage, null)
                .set(KbIndexTask::getStartedAt, null));
        if (affected > 0) {
            log.warn("回收 {} 个超时 RUNNING 任务，置回 PENDING 重新排队", affected);
        }
    }

    /**
     * BUILD/REPAIR 执行结果收尾：存在失败 chunk 或空文档时按失败处理（原 PARTIAL_SUCCESS 语义并入 FAILED），
     * 重试时 buildDocumentIndex 非 force 模式只处理失败 chunk，可收敛。
     */
    private void completeBuild(KbIndexTask task, IndexBuildResult result) {
        int total = nullToZero(result.getTotalChunkCount());
        int failed = nullToZero(result.getFailedCount());
        if (total == 0 || failed > 0) {
            recordFailure(task, new BusinessException(total == 0
                    ? "no chunks to index"
                    : "index build partially failed, failed chunks=" + failed));
            return;
        }
        finishSuccess(task, result.getTotalChunkCount(), result.getPendingChunkCount(),
                result.getSuccessCount(), result.getFailedCount(), result.getSkippedCount());
    }

    private void finishSuccess(KbIndexTask task, Integer total, Integer pending,
                               Integer success, Integer failed, Integer skipped) {
        LambdaUpdateWrapper<KbIndexTask> wrapper = new LambdaUpdateWrapper<KbIndexTask>()
                .eq(KbIndexTask::getId, task.getId())
                .set(KbIndexTask::getStatus, TaskStatus.SUCCESS.getValue())
                .set(KbIndexTask::getFinishedAt, LocalDateTime.now());
        if (total != null) {
            wrapper.set(KbIndexTask::getTotalChunkCount, total);
        }
        if (pending != null) {
            wrapper.set(KbIndexTask::getPendingChunkCount, pending);
        }
        if (success != null) {
            wrapper.set(KbIndexTask::getSuccessCount, success);
        }
        if (failed != null) {
            wrapper.set(KbIndexTask::getFailedCount, failed);
        }
        if (skipped != null) {
            wrapper.set(KbIndexTask::getSkippedCount, skipped);
        }
        indexTaskMapper.update(null, wrapper);
        log.info("索引任务执行成功: taskId={} type={} documentId={}",
                task.getId(), task.getTaskType(), task.getDocumentId());
    }

    /**
     * 构建中止（文档已失效）的收尾：直接置 FAILED 终态，不进入重试。
     */
    private void markAborted(KbIndexTask task, IndexBuildAbortedException e) {
        indexTaskMapper.update(null, new LambdaUpdateWrapper<KbIndexTask>()
                .eq(KbIndexTask::getId, task.getId())
                .set(KbIndexTask::getStatus, TaskStatus.FAILED.getValue())
                .set(KbIndexTask::getErrorMessage, limitErrorMessage(e.getMessage()))
                .set(KbIndexTask::getFinishedAt, LocalDateTime.now()));
        log.warn("索引任务因文档失效中止，置 FAILED 终态: taskId={} type={} documentId={}",
                task.getId(), task.getTaskType(), task.getDocumentId());
    }

    /**
     * 失败登记：未超最大重试次数 → RETRY_WAIT（指数退避后重试）；超限 → FAILED。
     */
    private void recordFailure(KbIndexTask task, Exception e) {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int nextRetry = retryCount + 1;
        String message = limitErrorMessage(e.getMessage());
        LocalDateTime now = LocalDateTime.now();

        if (nextRetry > maxRetryTimes) {
            indexTaskMapper.update(null, new LambdaUpdateWrapper<KbIndexTask>()
                    .eq(KbIndexTask::getId, task.getId())
                    .set(KbIndexTask::getStatus, TaskStatus.FAILED.getValue())
                    .set(KbIndexTask::getRetryCount, nextRetry)
                    .set(KbIndexTask::getErrorMessage, message)
                    .set(KbIndexTask::getFinishedAt, now));
            log.error("索引任务重试耗尽置 FAILED: taskId={} type={} documentId={}, 错误={}",
                    task.getId(), task.getTaskType(), task.getDocumentId(), message);
        } else {
            long delay = computeBackoffDelay(nextRetry);
            indexTaskMapper.update(null, new LambdaUpdateWrapper<KbIndexTask>()
                    .eq(KbIndexTask::getId, task.getId())
                    .set(KbIndexTask::getStatus, TaskStatus.RETRY_WAIT.getValue())
                    .set(KbIndexTask::getRetryCount, nextRetry)
                    .set(KbIndexTask::getErrorMessage, message)
                    .set(KbIndexTask::getNextRetryAt, now.plus(delay, ChronoUnit.MILLIS)));
            log.warn("索引任务失败，{}ms 后第 {} 次重试: taskId={} type={} documentId={}, 错误={}",
                    delay, nextRetry, task.getId(), task.getTaskType(), task.getDocumentId(), message);
        }
    }

    /**
     * 指数退避：base × 2^(attempt-1)，封顶 retryMaxDelayMs。
     */
    private long computeBackoffDelay(int attempt) {
        long delay = retryBaseDelayMs * (1L << (attempt - 1));
        return Math.min(delay, retryMaxDelayMs);
    }

    private IndexTaskResponse toResponse(KbIndexTask task) {
        return new IndexTaskResponse()
                .setTaskId(task.getId())
                .setDocumentId(task.getDocumentId())
                .setSpaceId(task.getSpaceId())
                .setTaskType(task.getTaskType())
                .setStatus(task.getStatus())
                .setStatusText(toStatusText(task.getStatus()))
                .setRetryCount(task.getRetryCount())
                .setNextRetryAt(task.getNextRetryAt())
                .setTotalChunkCount(task.getTotalChunkCount())
                .setPendingChunkCount(task.getPendingChunkCount())
                .setSuccessCount(task.getSuccessCount())
                .setFailedCount(task.getFailedCount())
                .setSkippedCount(task.getSkippedCount())
                .setErrorMessage(task.getErrorMessage())
                .setStartedAt(task.getStartedAt())
                .setFinishedAt(task.getFinishedAt())
                .setCreatedAt(task.getCreatedAt())
                .setUpdatedAt(task.getUpdatedAt());
    }

    private String toStatusText(Integer status) {
        if (status == null) {
            return "unknown";
        }
        return switch (TaskStatus.of(status)) {
            case PENDING -> "pending";
            case RUNNING -> "running";
            case RETRY_WAIT -> "retry_wait";
            case SUCCESS -> "success";
            case FAILED -> "failed";
        };
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String limitErrorMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Unknown error";
        }
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
