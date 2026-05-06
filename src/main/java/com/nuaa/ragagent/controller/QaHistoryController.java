package com.nuaa.ragagent.controller;

import com.nuaa.ragagent.common.ApiResponse;
import com.nuaa.ragagent.response.QaHistoryResponse;
import com.nuaa.ragagent.response.QaMessageResponse;
import com.nuaa.ragagent.response.QaReferenceResponse;
import com.nuaa.ragagent.response.QaSessionResponse;
import com.nuaa.ragagent.service.QaHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/qa/history")
public class QaHistoryController {

    private final QaHistoryService qaHistoryService;

    public QaHistoryController(QaHistoryService qaHistoryService) {
        this.qaHistoryService = qaHistoryService;
    }

    @GetMapping("/spaces/{spaceId}/sessions")
    public ApiResponse<List<QaSessionResponse>> listSessionsBySpaceId(@PathVariable Long spaceId) {
        return ApiResponse.success(qaHistoryService.listSessionsBySpaceId(spaceId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<QaMessageResponse>> listMessagesBySessionId(@PathVariable Long sessionId) {
        return ApiResponse.success(qaHistoryService.listMessagesBySessionId(sessionId));
    }

    @GetMapping("/messages/{messageId}/references")
    public ApiResponse<List<QaReferenceResponse>> listReferencesByMessageId(@PathVariable Long messageId) {
        return ApiResponse.success(qaHistoryService.listReferencesByMessageId(messageId));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<QaHistoryResponse> getSessionHistory(@PathVariable Long sessionId) {
        return ApiResponse.success(qaHistoryService.getSessionHistory(sessionId));
    }
}