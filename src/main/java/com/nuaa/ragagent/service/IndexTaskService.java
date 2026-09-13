package com.nuaa.ragagent.service;

import com.nuaa.ragagent.entity.KbIndexTask;
import com.nuaa.ragagent.enums.TaskType;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.response.IndexTaskResponse;

import java.util.List;

/**
 * 持久化异步索引任务服务。
 * <p>文档操作（创建/删除/更新）不再直接执行索引工作，而是登记 BUILD_INDEX / DELETE_INDEX 任务，
 * 由 {@link IndexTaskWorker} 轮询调度执行；REPAIR_INDEX 由周期对账登记。</p>
 *
 * @author jiyunhe
 */
public interface IndexTaskService {

    /**
     * 登记一个索引任务（PENDING）。
     * <p>去重：同一 Document + 同一任务类型下存在活跃任务（PENDING/RUNNING/RETRY_WAIT）时
     * 直接返回已有任务，不重复登记。</p>
     *
     * @param documentId 文档 ID，不能为空
     * @param taskType   任务类型，不能为空
     * @return 任务详情（新登记或已存在的活跃任务）
     * @throws BusinessException documentId/taskType 为空，或文档不存在、对失效文档登记 BUILD/REPAIR 时抛出
     */
    IndexTaskResponse createTask(Long documentId, TaskType taskType);

    /**
     * 查询任务详情。
     *
     * @param taskId 任务 ID，不能为空
     * @return 任务详情
     * @throws BusinessException taskId 为空或任务不存在时抛出
     */
    IndexTaskResponse getTask(Long taskId);

    /**
     * 按文档 ID 查询任务列表，按任务 ID 倒序排列。
     *
     * @param documentId 文档 ID，不能为空
     * @return 该文档的任务列表
     * @throws BusinessException documentId 为空时抛出
     */
    List<IndexTaskResponse> listDocumentTasks(Long documentId);

    /**
     * 人工重试 FAILED 任务：重置为 PENDING，清空错误信息与重试计数。
     *
     * @param taskId 任务 ID，不能为空
     * @return 重置后的任务详情
     * @throws BusinessException taskId 为空、任务不存在或非 FAILED 状态时抛出
     */
    IndexTaskResponse retryTask(Long taskId);

    /**
     * 供 worker 调用：拉取可执行任务（PENDING，或已到重试时间的 RETRY_WAIT）。
     *
     * @param limit 单批拉取上限
     * @return 可执行任务列表
     */
    List<KbIndexTask> findRunnableTasks(int limit);

    /**
     * 供 worker 调用：条件更新抢占任务（PENDING/RETRY_WAIT → RUNNING）。
     * <p>通过影响行数判断是否抢到，避免并发/重复调度下同一任务被执行两次。</p>
     *
     * @param task 待抢占任务（worker 已查到的）
     * @return true 抢占成功，false 已被其他 worker 抢占或状态已变化
     */
    boolean tryAcquire(KbIndexTask task);

    /**
     * 异步执行任务（@Async，线程池 indexTaskExecutor）。
     * <p>按任务类型分发执行体，成功后置 SUCCESS，失败后按指数退避转 RETRY_WAIT 或置 FAILED。</p>
     *
     * @param task 已抢占为 RUNNING 的任务
     */
    void executeTask(KbIndexTask task);

    /**
     * 回收超时 RUNNING 任务（进程被杀遗留）：置回 PENDING 并重试计数 +1，重新排队执行。
     */
    void recoverRunningTasks();
}
