package com.nuaa.ragagent.service;

import com.nuaa.ragagent.request.AskRequest;
import com.nuaa.ragagent.response.AskResponse;
import com.nuaa.ragagent.response.QaHistoryResponse;
import com.nuaa.ragagent.response.QaHistorySaveResult;
import com.nuaa.ragagent.response.QaMessageResponse;
import com.nuaa.ragagent.response.QaReferenceResponse;
import com.nuaa.ragagent.response.QaSessionResponse;

import java.util.List;

public interface QaHistoryService {

    QaHistorySaveResult saveAskHistory(AskRequest request, AskResponse response);

    List<QaSessionResponse> listSessionsBySpaceId(Long spaceId);

    List<QaMessageResponse> listMessagesBySessionId(Long sessionId);

    List<QaReferenceResponse> listReferencesByMessageId(Long messageId);

    QaHistoryResponse getSessionHistory(Long sessionId);
}