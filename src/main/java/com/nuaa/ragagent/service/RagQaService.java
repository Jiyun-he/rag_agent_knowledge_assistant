package com.nuaa.ragagent.service;

import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.request.AskRequest;
import com.nuaa.ragagent.response.AskResponse;

/**
 * RAG 智能问答服务接口。
 *
 * @author jiyunhe
 */
public interface RagQaService {

    /**
     * 执行一次 RAG 问答。
     * <p>先检索知识库中的相关分块，检索到内容时基于分块生成回答并附带引用来源；
     * 未检索到内容时返回兜底回答。问答结果会同步保存到历史记录并回填会话信息。</p>
     *
     * @param request 问答请求，包含空间 ID 与问题，不能为空
     * @return 问答结果，包含回答内容、引用来源与会话信息
     * @throws BusinessException 当请求为空、空间 ID 为空或问题为空时抛出
     */
    AskResponse ask(AskRequest request);
}
