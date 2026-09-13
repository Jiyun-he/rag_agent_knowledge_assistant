package com.nuaa.ragagent.service.impl;

import com.nuaa.ragagent.entity.KbIndexTask;
import com.nuaa.ragagent.service.IndexTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
/**
 * 持久化索引任务调度器。
 *
 * <ul>
 *     <li>轮询拉取可执行任务（PENDING / 已到重试时间的 RETRY_WAIT），条件更新抢占后提交异步执行</li>
 *     <li>周期回收超时 RUNNING 任务（进程被杀后遗留）重新排队</li>
 * </ul>
 *
 * @author jiyunhe
 */
@Component
public class IndexTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(IndexTaskWorker.class);

    private final IndexTaskService indexTaskService;

    private final int batchSize;

    public IndexTaskWorker(IndexTaskService indexTaskService,
                           @Value("${rag.task.worker-batch-size:10}") int batchSize) {
        this.indexTaskService = indexTaskService;
        this.batchSize = batchSize;
    }

    /**
     * 轮询并提交可执行任务。抢占使用条件更新，多个实例/多轮不会重复执行同一任务；
     * 执行体为 @Async，此处只负责抢占与提交。
     */
    @Scheduled(fixedDelayString = "${rag.task.worker-poll-ms:2000}",
            initialDelayString = "${rag.task.worker-initial-delay-ms:5000}")
    public void pollAndSubmit() {
        try {
            List<KbIndexTask> tasks = indexTaskService.findRunnableTasks(batchSize);
            for (KbIndexTask task : tasks) {
                if (indexTaskService.tryAcquire(task)) {
                    indexTaskService.executeTask(task);
                }
            }
        } catch (Exception e) {
            log.warn("轮询索引任务失败: {}", e.getMessage());
        }
    }

    /**
     * 周期回收超时 RUNNING 任务：进程被 kill 后任务会一直停留在 RUNNING，
     * 置回 PENDING 并计入重试，重启后也能继续执行。
     */
    @Scheduled(fixedDelayString = "${rag.task.recover-poll-ms:30000}",
            initialDelayString = "60000")
    public void recoverStuckTasks() {
        try {
            indexTaskService.recoverRunningTasks();
        } catch (Exception e) {
            log.warn("回收超时 RUNNING 任务失败: {}", e.getMessage());
        }
    }
}
