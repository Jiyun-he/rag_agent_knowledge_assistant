package com.nuaa.ragagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.entity.KbEmbeddingTask;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.mapper.KbDocumentMapper;
import com.nuaa.ragagent.mapper.KbEmbeddingTaskMapper;
import com.nuaa.ragagent.response.EmbeddingTaskResponse;
import com.nuaa.ragagent.response.VectorizeDocumentResponse;
import com.nuaa.ragagent.service.EmbeddingTaskService;
import com.nuaa.ragagent.service.KnowledgeEmbeddingService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmbeddingTaskServiceImpl implements EmbeddingTaskService {

    private static final String TASK_TYPE_DOCUMENT_VECTORIZE = "DOCUMENT_VECTORIZE";

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_SUCCESS = 2;
    private static final int STATUS_FAILED = 3;
    private static final int STATUS_PARTIAL_SUCCESS = 4;

    private final KbEmbeddingTaskMapper embeddingTaskMapper;
    private final KbDocumentMapper documentMapper;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;

    public EmbeddingTaskServiceImpl(KbEmbeddingTaskMapper embeddingTaskMapper,
                                    KbDocumentMapper documentMapper,
                                    KnowledgeEmbeddingService knowledgeEmbeddingService) {
        this.embeddingTaskMapper = embeddingTaskMapper;
        this.documentMapper = documentMapper;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
    }

    @Override
    public EmbeddingTaskResponse createTask(Long documentId) {
        if (documentId == null) {
            throw new BusinessException("documentId must not be null");
        }

        KbDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException("Document not found: " + documentId);
        }

        KbEmbeddingTask task = new KbEmbeddingTask()
                .setDocumentId(document.getId())
                .setSpaceId(document.getSpaceId())
                .setTaskType(TASK_TYPE_DOCUMENT_VECTORIZE)
                .setStatus(STATUS_PENDING)
                .setTotalChunkCount(0)
                .setPendingChunkCount(0)
                .setSuccessCount(0)
                .setFailedCount(0)
                .setSkippedCount(0)
                .setErrorMessage(null)
                .setStartedAt(null)
                .setFinishedAt(null);

        embeddingTaskMapper.insert(task);

        return toResponse(task);
    }

    @Async("embeddingTaskExecutor")
    @Override
    public void runTaskAsync(Long taskId) {
        KbEmbeddingTask task = embeddingTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }

        markRunning(task);

        try {
            VectorizeDocumentResponse result = knowledgeEmbeddingService.vectorizeDocument(task.getDocumentId());

            KbEmbeddingTask updateTask = new KbEmbeddingTask()
                    .setId(task.getId())
                    .setTotalChunkCount(nullToZero(result.getTotalChunkCount()))
                    .setPendingChunkCount(nullToZero(result.getPendingChunkCount()))
                    .setSuccessCount(nullToZero(result.getSuccessCount()))
                    .setFailedCount(nullToZero(result.getFailedCount()))
                    .setSkippedCount(nullToZero(result.getSkippedCount()))
                    .setStatus(resolveFinalStatus(result))
                    .setErrorMessage(resolveResultMessage(result))
                    .setFinishedAt(LocalDateTime.now());

            embeddingTaskMapper.updateById(updateTask);
        } catch (Exception e) {
            KbEmbeddingTask failedTask = new KbEmbeddingTask()
                    .setId(task.getId())
                    .setStatus(STATUS_FAILED)
                    .setErrorMessage(limitErrorMessage(e.getMessage()))
                    .setFinishedAt(LocalDateTime.now());

            embeddingTaskMapper.updateById(failedTask);
        }
    }

    @Override
    public EmbeddingTaskResponse getTask(Long taskId) {
        if (taskId == null) {
            throw new BusinessException("taskId must not be null");
        }

        KbEmbeddingTask task = embeddingTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Embedding task not found: " + taskId);
        }

        return toResponse(task);
    }

    @Override
    public List<EmbeddingTaskResponse> listDocumentTasks(Long documentId) {
        if (documentId == null) {
            throw new BusinessException("documentId must not be null");
        }

        LambdaQueryWrapper<KbEmbeddingTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbEmbeddingTask::getDocumentId, documentId)
                .orderByDesc(KbEmbeddingTask::getId);

        return embeddingTaskMapper.selectList(wrapper)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private void markRunning(KbEmbeddingTask task) {
        KbEmbeddingTask runningTask = new KbEmbeddingTask()
                .setId(task.getId())
                .setStatus(STATUS_RUNNING)
                .setStartedAt(LocalDateTime.now())
                .setErrorMessage(null);

        embeddingTaskMapper.updateById(runningTask);
    }

    private int resolveFinalStatus(VectorizeDocumentResponse result) {
        int total = nullToZero(result.getTotalChunkCount());
        int success = nullToZero(result.getSuccessCount());
        int failed = nullToZero(result.getFailedCount());
        int skipped = nullToZero(result.getSkippedCount());

        if (total == 0) {
            return STATUS_FAILED;
        }

        if (failed == 0) {
            return STATUS_SUCCESS;
        }

        if (success > 0 || skipped > 0) {
            return STATUS_PARTIAL_SUCCESS;
        }

        return STATUS_FAILED;
    }

    private String resolveResultMessage(VectorizeDocumentResponse result) {
        int total = nullToZero(result.getTotalChunkCount());
        int failed = nullToZero(result.getFailedCount());
        int success = nullToZero(result.getSuccessCount());
        int skipped = nullToZero(result.getSkippedCount());

        if (total == 0) {
            return "No chunks found for vectorization.";
        }

        if (failed == 0) {
            return null;
        }

        if (success > 0 || skipped > 0) {
            return "Some chunks failed during vectorization.";
        }

        return "All pending chunks failed during vectorization.";
    }

    private EmbeddingTaskResponse toResponse(KbEmbeddingTask task) {
        return new EmbeddingTaskResponse()
                .setTaskId(task.getId())
                .setDocumentId(task.getDocumentId())
                .setSpaceId(task.getSpaceId())
                .setTaskType(task.getTaskType())
                .setStatus(task.getStatus())
                .setStatusText(toStatusText(task.getStatus()))
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

        if (status == STATUS_PENDING) {
            return "pending";
        }

        if (status == STATUS_RUNNING) {
            return "running";
        }

        if (status == STATUS_SUCCESS) {
            return "success";
        }

        if (status == STATUS_FAILED) {
            return "failed";
        }

        if (status == STATUS_PARTIAL_SUCCESS) {
            return "partial_success";
        }

        return "unknown";
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String limitErrorMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Unknown error";
        }

        if (message.length() <= 1000) {
            return message;
        }

        return message.substring(0, 1000);
    }
}