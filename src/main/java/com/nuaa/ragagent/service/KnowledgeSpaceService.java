package com.nuaa.ragagent.service;

import com.nuaa.ragagent.entity.KbSpace;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.request.CreateSpaceRequest;
import com.nuaa.ragagent.request.UpdateSpaceRequest;

import java.util.List;

/**
 * 知识库空间管理服务接口。
 *
 * @author jiyunhe
 */
public interface KnowledgeSpaceService {

    /**
     * 创建知识库空间。
     * <p>校验空间名称唯一后，保存新建空间并返回。</p>
     *
     * @param request 创建空间请求，包含名称与描述，不能为空
     * @return 创建成功的空间信息
     * @throws BusinessException 当空间名称已存在时抛出
     */
    KbSpace create(CreateSpaceRequest request);

    /**
     * 查询正常状态下的全部空间，按 ID 倒序排列。
     *
     * @return 空间列表
     */
    List<KbSpace> list();

    /**
     * 按 ID 查询空间。
     *
     * @param id 空间 ID，不能为空
     * @return 正常状态下的空间信息
     * @throws BusinessException 当空间不存在或已删除时抛出
     */
    KbSpace getById(Long id);

    /**
     * 更新空间信息。
     * <p>校验空间存在及名称唯一后，更新空间的名称与描述并返回更新后的信息。</p>
     *
     * @param id      要更新的空间 ID
     * @param request 更新空间请求，包含新的名称与描述，不能为空
     * @return 更新后的空间信息
     * @throws BusinessException 当空间不存在，或新名称已被其他空间占用时抛出
     */
    KbSpace update(Long id, UpdateSpaceRequest request);

    /**
     * 逻辑删除空间。
     * <p>校验空间存在且不含任何正常状态的文档后，将空间标记为已删除（status = 0）。</p>
     *
     * @param id 要删除的空间 ID
     * @return 删除成功返回 true
     * @throws BusinessException 当空间不存在，或空间内仍存在正常文档时抛出
     */
    Boolean delete(Long id);
}
