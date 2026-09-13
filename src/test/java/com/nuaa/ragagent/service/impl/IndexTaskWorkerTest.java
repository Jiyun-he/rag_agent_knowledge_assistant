package com.nuaa.ragagent.service.impl;

import com.nuaa.ragagent.entity.KbIndexTask;
import com.nuaa.ragagent.enums.TaskStatus;
import com.nuaa.ragagent.service.IndexTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证调度器：轮询拉取可执行任务 → 条件抢占成功才提交执行；抢占失败（已被其他实例抢占）不提交。
 */
@ExtendWith(MockitoExtension.class)
class IndexTaskWorkerTest {

    @Mock
    private IndexTaskService indexTaskService;

    private IndexTaskWorker worker;

    @BeforeEach
    void setUp() {
        worker = new IndexTaskWorker(indexTaskService, 10);
    }

    @Test
    void pollAndSubmit_acquiredTasksExecutedMissedTasksSkipped() {
        KbIndexTask t1 = new KbIndexTask().setId(1L).setStatus(TaskStatus.PENDING.getValue());
        KbIndexTask t2 = new KbIndexTask().setId(2L).setStatus(TaskStatus.RETRY_WAIT.getValue());
        when(indexTaskService.findRunnableTasks(10)).thenReturn(List.of(t1, t2));
        when(indexTaskService.tryAcquire(t1)).thenReturn(true);
        when(indexTaskService.tryAcquire(t2)).thenReturn(false); // 已被其他 worker 抢占

        worker.pollAndSubmit();

        verify(indexTaskService).executeTask(t1);
        verify(indexTaskService, never()).executeTask(t2);
    }

    @Test
    void pollAndSubmit_serviceThrows_swallowedWithoutAcquire() {
        when(indexTaskService.findRunnableTasks(10)).thenThrow(new RuntimeException("db down"));

        worker.pollAndSubmit();

        verify(indexTaskService, never()).tryAcquire(any());
    }

    @Test
    void recoverStuckTasks_delegatesToService() {
        worker.recoverStuckTasks();
        verify(indexTaskService).recoverRunningTasks();
    }
}
