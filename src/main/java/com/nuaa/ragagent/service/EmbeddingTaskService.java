package com.nuaa.ragagent.service;

import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.response.EmbeddingTaskResponse;

import java.util.List;

/**
 * 文档向量化任务服务接口。
 *
 * @author jiyunhe
 */
public interface EmbeddingTaskService {

    /**
     * 创建文档向量化任务。
     * <p>校验文档存在后，初始化一个状态为待处理（pending）的向量化任务并持久化到数据库。</p>
     *
     * @param documentId 需要向量化的文档 ID，不能为空
     * @return 创建成功的向量化任务详情
     * @throws BusinessException 当 documentId 为空，或对应文档不存在时抛出
     */
    EmbeddingTaskResponse createTask(Long documentId);

    /**
     * 异步执行向量化任务。
     * <p>将任务标记为运行中后，调用向量化服务处理文档，并根据结果更新任务的统计信息、
     * 最终状态及错误信息；任务不存在时直接忽略。</p>
     *
     * @param taskId 待执行的向量化任务 ID
     */
    void runTaskAsync(Long taskId);

    /**
     * 查询向量化任务详情。
     *
     * @param taskId 向量化任务 ID，不能为空
     * @return 任务详情
     * @throws BusinessException 当 taskId 为空，或任务不存在时抛出
     */
    EmbeddingTaskResponse getTask(Long taskId);

    /**
     * 按文档 ID 查询该文档的全部向量化任务，按任务 ID 倒序排列。
     *
     * @param documentId 文档 ID，不能为空
     * @return 该文档的向量化任务列表
     * @throws BusinessException 当 documentId 为空时抛出
     */
    List<EmbeddingTaskResponse> listDocumentTasks(Long documentId);
}
