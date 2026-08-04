package com.nuaa.ragagent.service;

import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.request.AskRequest;
import com.nuaa.ragagent.response.AskResponse;
import com.nuaa.ragagent.response.QaHistoryResponse;
import com.nuaa.ragagent.response.QaHistorySaveResult;
import com.nuaa.ragagent.response.QaMessageResponse;
import com.nuaa.ragagent.response.QaReferenceResponse;
import com.nuaa.ragagent.response.QaSessionResponse;

import java.util.List;

/**
 * 问答历史记录服务接口。
 *
 * @author jiyunhe
 */
public interface QaHistoryService {

    /**
     * 保存一次问答记录。
     * <p>获取或创建会话后，保存用户问题与助手回答两条消息，并将回答引用的知识块保存为引用记录，
     * 最后刷新会话更新时间，返回保存结果。</p>
     *
     * @param request  问答请求，包含空间 ID、问题及可选会话 ID，不能为空
     * @param response 问答响应，包含回答内容与引用信息，不能为空
     * @return 保存结果，包含会话 ID 与用户/助手消息 ID
     * @throws BusinessException 当请求或响应为空、空间 ID 或问题为空，或指定会话不存在/不属于该空间时抛出
     */
    QaHistorySaveResult saveAskHistory(AskRequest request, AskResponse response);

    /**
     * 按空间 ID 查询该空间下的全部会话，按更新时间倒序排列。
     *
     * @param spaceId 空间 ID，不能为空
     * @return 会话列表
     * @throws BusinessException 当 spaceId 为空时抛出
     */
    List<QaSessionResponse> listSessionsBySpaceId(Long spaceId);

    /**
     * 按会话 ID 查询会话内的全部消息，按创建时间升序排列。
     *
     * @param sessionId 会话 ID，不能为空
     * @return 消息列表
     * @throws BusinessException 当 sessionId 为空或会话不存在时抛出
     */
    List<QaMessageResponse> listMessagesBySessionId(Long sessionId);

    /**
     * 按消息 ID 查询该消息关联的引用记录。
     *
     * @param messageId 消息 ID，不能为空
     * @return 引用记录列表
     * @throws BusinessException 当 messageId 为空或消息不存在时抛出
     */
    List<QaReferenceResponse> listReferencesByMessageId(Long messageId);

    /**
     * 查询会话的完整历史。
     * <p>返回会话信息及全部消息，助手消息附带其引用的知识块记录。</p>
     *
     * @param sessionId 会话 ID，不能为空
     * @return 会话历史详情，包含会话信息与消息列表
     * @throws BusinessException 当 sessionId 为空或会话不存在时抛出
     */
    QaHistoryResponse getSessionHistory(Long sessionId);
}
