package com.nuaa.ragagent.service;

import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.request.CreateDocumentRequest;
import com.nuaa.ragagent.request.UpdateDocumentRequest;

import java.util.List;

/**
 * 知识库文档管理服务接口。
 *
 * @author jiyunhe
 */
public interface KnowledgeDocumentService {

    /**
     * 创建知识库文档。
     * <p>校验所属空间存在后，保存文档并按内容自动分块，最后返回包含分块数量的完整文档信息。</p>
     *
     * @param request 创建文档请求，包含空间 ID、标题、内容等信息，不能为空
     * @return 创建成功的文档信息
     * @throws BusinessException 当所属空间不存在时抛出
     */
    KbDocument create(CreateDocumentRequest request);

    /**
     * 查询正常状态下的文档列表，按 ID 倒序排列；可指定空间进行过滤。
     *
     * @param spaceId 空间 ID；为空时查询全部空间的文档
     * @return 文档列表
     */
    List<KbDocument> list(Long spaceId);

    /**
     * 按 ID 查询文档。
     *
     * @param id 文档 ID，不能为空
     * @return 正常状态下的文档信息
     * @throws BusinessException 当文档不存在或已删除时抛出
     */
    KbDocument getById(Long id);

    /**
     * 更新文档信息。
     * <p>校验目标文档与目标空间后更新文档字段，并按新内容重新分块，返回更新后的完整文档。</p>
     *
     * @param id      要更新的文档 ID
     * @param request 更新文档请求，包含新的空间、标题、内容等信息，不能为空
     * @return 更新后的文档信息
     * @throws BusinessException 当文档不存在，或目标空间不存在时抛出
     */
    KbDocument update(Long id, UpdateDocumentRequest request);

    /**
     * 逻辑删除文档。
     * <p>将文档及其所有分块标记为已删除（status = 0），不物理删除数据。</p>
     *
     * @param id 要删除的文档 ID
     * @return 删除成功返回 true
     * @throws BusinessException 当文档不存在时抛出
     */
    Boolean delete(Long id);

    /**
     * 查询文档的全部分块，按分块索引升序排列。
     *
     * @param documentId 文档 ID
     * @return 该文档的分块列表
     * @throws BusinessException 当文档不存在时抛出
     */
    List<KbChunk> listChunks(Long documentId);
}
