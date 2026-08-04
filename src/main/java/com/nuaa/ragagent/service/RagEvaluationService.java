package com.nuaa.ragagent.service;

import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.request.CompareEvalRunsRequest;
import com.nuaa.ragagent.request.CreateEvalCaseRequest;
import com.nuaa.ragagent.request.CreateEvalDatasetRequest;
import com.nuaa.ragagent.request.StartEvalRunRequest;
import com.nuaa.ragagent.response.EvalCaseResponse;
import com.nuaa.ragagent.response.EvalCaseResultResponse;
import com.nuaa.ragagent.response.EvalDatasetResponse;
import com.nuaa.ragagent.response.EvalRunCompareResponse;
import com.nuaa.ragagent.response.EvalRunResponse;

import java.util.List;

/**
 * RAG 评测服务接口。
 *
 * @author jiyunhe
 */
public interface RagEvaluationService {

    /**
     * 创建评测数据集。
     * <p>校验空间 ID 与数据集名称后，保存新的评测数据集并返回。</p>
     *
     * @param request 创建评测数据集请求，包含空间 ID 与名称，不能为空
     * @return 创建成功的数据集信息
     * @throws BusinessException 当请求为空、空间 ID 为空或数据集名称为空时抛出
     */
    EvalDatasetResponse createDataset(CreateEvalDatasetRequest request);

    /**
     * 查询全部正常状态下的评测数据集，按创建时间倒序排列。
     *
     * @return 评测数据集列表
     */
    List<EvalDatasetResponse> listDatasets();

    /**
     * 按 ID 查询评测数据集。
     *
     * @param datasetId 数据集 ID，不能为空
     * @return 正常状态下的数据集信息
     * @throws BusinessException 当数据集不存在或已删除时抛出
     */
    EvalDatasetResponse getDataset(Long datasetId);

    /**
     * 在指定评测数据集下创建评测用例。
     * <p>校验数据集与问题后，保存评测用例，期望答案、期望分块与期望关键词以 JSON 形式存储。</p>
     *
     * @param datasetId 评测数据集 ID
     * @param request   创建评测用例请求，包含问题等，不能为空
     * @return 创建成功的评测用例信息
     * @throws BusinessException 当数据集不存在、请求为空或问题为空时抛出
     */
    EvalCaseResponse createCase(Long datasetId, CreateEvalCaseRequest request);

    /**
     * 查询评测数据集下的全部评测用例，按 ID 升序排列。
     *
     * @param datasetId 评测数据集 ID
     * @return 评测用例列表
     * @throws BusinessException 当数据集不存在时抛出
     */
    List<EvalCaseResponse> listCases(Long datasetId);

    /**
     * 启动一次评测运行。
     * <p>校验请求与数据集后，对数据集内每个评测用例执行检索与（可选的）答案生成，
     * 计算召回率、命中率、MRR 等指标，汇总生成评测运行结果并返回。</p>
     *
     * @param request 启动评测运行请求，包含数据集 ID 与检索参数，不能为空
     * @return 评测运行结果，包含各指标汇总
     * @throws BusinessException 当请求为空、数据集 ID 为空、数据集不存在或评测集中没有可执行的用例时抛出
     */
    EvalRunResponse startRun(StartEvalRunRequest request);

    /**
     * 按 ID 查询评测运行。
     *
     * @param runId 评测运行 ID
     * @return 评测运行信息
     * @throws BusinessException 当评测运行不存在时抛出
     */
    EvalRunResponse getRun(Long runId);

    /**
     * 查询一次评测运行的全部用例级结果，按结果 ID 升序排列。
     *
     * @param runId 评测运行 ID
     * @return 用例级评测结果列表
     * @throws BusinessException 当评测运行不存在时抛出
     */
    List<EvalCaseResultResponse> listRunResults(Long runId);

    /**
     * 对比多次评测运行。
     * <p>以第一个运行作为基准，计算其余运行在召回率、命中率、MRR、延迟等指标上的相对变化（delta），
     * 并挑选各项指标最优及耗时最低的运行，汇总生成对比结果。</p>
     *
     * @param request 对比请求，包含至少两个运行 ID，不能为空
     * @return 运行对比结果，包含各运行指标、delta 与最优运行标识
     * @throws BusinessException 当请求为空、运行 ID 为空或不足两个，或存在不存在的运行时抛出
     */
    EvalRunCompareResponse compareRuns(CompareEvalRunsRequest request);
}
