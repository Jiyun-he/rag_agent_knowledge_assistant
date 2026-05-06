package com.nuaa.ragagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nuaa.ragagent.entity.QaMessage;
import com.nuaa.ragagent.entity.QaReference;
import com.nuaa.ragagent.entity.QaSession;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.mapper.QaMessageMapper;
import com.nuaa.ragagent.mapper.QaReferenceMapper;
import com.nuaa.ragagent.mapper.QaSessionMapper;
import com.nuaa.ragagent.request.AskRequest;
import com.nuaa.ragagent.response.AskResponse;
import com.nuaa.ragagent.response.QaHistoryResponse;
import com.nuaa.ragagent.response.QaHistorySaveResult;
import com.nuaa.ragagent.response.QaMessageResponse;
import com.nuaa.ragagent.response.QaReferenceResponse;
import com.nuaa.ragagent.response.QaSessionResponse;
import com.nuaa.ragagent.response.RagAnswerReferenceResponse;
import com.nuaa.ragagent.service.QaHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class QaHistoryServiceImpl implements QaHistoryService {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private final QaSessionMapper qaSessionMapper;

    private final QaMessageMapper qaMessageMapper;

    private final QaReferenceMapper qaReferenceMapper;

    public QaHistoryServiceImpl(QaSessionMapper qaSessionMapper,
                                QaMessageMapper qaMessageMapper,
                                QaReferenceMapper qaReferenceMapper) {
        this.qaSessionMapper = qaSessionMapper;
        this.qaMessageMapper = qaMessageMapper;
        this.qaReferenceMapper = qaReferenceMapper;
    }

    @Override
    @Transactional
    public QaHistorySaveResult saveAskHistory(AskRequest request, AskResponse response) {
        if (request == null) {
            throw new BusinessException("Ask request must not be null");
        }
        if (response == null) {
            throw new BusinessException("Ask response must not be null");
        }
        if (request.getSpaceId() == null) {
            throw new BusinessException("spaceId must not be null");
        }
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            throw new BusinessException("question must not be empty");
        }

        QaSession session = getOrCreateSession(request);

        QaMessage userMessage = new QaMessage()
                .setSessionId(session.getId())
                .setSpaceId(request.getSpaceId())
                .setRole(ROLE_USER)
                .setContent(request.getQuestion());

        qaMessageMapper.insert(userMessage);

        QaMessage assistantMessage = new QaMessage()
                .setSessionId(session.getId())
                .setSpaceId(request.getSpaceId())
                .setRole(ROLE_ASSISTANT)
                .setContent(response.getAnswer() == null ? "" : response.getAnswer());

        qaMessageMapper.insert(assistantMessage);

        saveReferences(session.getId(), assistantMessage.getId(), response.getReferences());

        QaSession updateSession = new QaSession()
                .setId(session.getId())
                .setUpdatedAt(LocalDateTime.now());
        qaSessionMapper.updateById(updateSession);

        return new QaHistorySaveResult()
                .setSessionId(session.getId())
                .setUserMessageId(userMessage.getId())
                .setAssistantMessageId(assistantMessage.getId());
    }

    @Override
    public List<QaSessionResponse> listSessionsBySpaceId(Long spaceId) {
        if (spaceId == null) {
            throw new BusinessException("spaceId must not be null");
        }

        LambdaQueryWrapper<QaSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaSession::getSpaceId, spaceId)
                .eq(QaSession::getStatus, 1)
                .orderByDesc(QaSession::getUpdatedAt)
                .orderByDesc(QaSession::getCreatedAt);

        List<QaSession> sessions = qaSessionMapper.selectList(wrapper);
        List<QaSessionResponse> responses = new ArrayList<>();

        for (QaSession session : sessions) {
            responses.add(toSessionResponse(session));
        }

        return responses;
    }

    @Override
    public List<QaMessageResponse> listMessagesBySessionId(Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException("sessionId must not be null");
        }

        QaSession session = qaSessionMapper.selectById(sessionId);
        if (session == null || session.getStatus() == null || session.getStatus() != 1) {
            throw new BusinessException("QA session not found");
        }

        LambdaQueryWrapper<QaMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaMessage::getSessionId, sessionId)
                .orderByAsc(QaMessage::getCreatedAt)
                .orderByAsc(QaMessage::getId);

        List<QaMessage> messages = qaMessageMapper.selectList(wrapper);
        List<QaMessageResponse> responses = new ArrayList<>();

        for (QaMessage message : messages) {
            responses.add(toMessageResponse(message, null));
        }

        return responses;
    }

    @Override
    public List<QaReferenceResponse> listReferencesByMessageId(Long messageId) {
        if (messageId == null) {
            throw new BusinessException("messageId must not be null");
        }

        QaMessage message = qaMessageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException("QA message not found");
        }

        return listReferencesByAssistantMessageId(messageId);
    }

    @Override
    public QaHistoryResponse getSessionHistory(Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException("sessionId must not be null");
        }

        QaSession session = qaSessionMapper.selectById(sessionId);
        if (session == null || session.getStatus() == null || session.getStatus() != 1) {
            throw new BusinessException("QA session not found");
        }

        LambdaQueryWrapper<QaMessage> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.eq(QaMessage::getSessionId, sessionId)
                .orderByAsc(QaMessage::getCreatedAt)
                .orderByAsc(QaMessage::getId);

        List<QaMessage> messages = qaMessageMapper.selectList(messageWrapper);
        List<QaMessageResponse> messageResponses = new ArrayList<>();

        for (QaMessage message : messages) {
            List<QaReferenceResponse> references = null;
            if (ROLE_ASSISTANT.equals(message.getRole())) {
                references = listReferencesByAssistantMessageId(message.getId());
            }
            messageResponses.add(toMessageResponse(message, references));
        }

        return new QaHistoryResponse()
                .setSession(toSessionResponse(session))
                .setMessages(messageResponses);
    }

    private QaSession getOrCreateSession(AskRequest request) {
        if (request.getSessionId() != null) {
            QaSession existingSession = qaSessionMapper.selectById(request.getSessionId());

            if (existingSession == null || existingSession.getStatus() == null || existingSession.getStatus() != 1) {
                throw new BusinessException("QA session not found");
            }

            if (!existingSession.getSpaceId().equals(request.getSpaceId())) {
                throw new BusinessException("sessionId does not belong to the specified spaceId");
            }

            return existingSession;
        }

        QaSession newSession = new QaSession()
                .setSpaceId(request.getSpaceId())
                .setTitle(buildSessionTitle(request.getQuestion()))
                .setStatus(1);

        qaSessionMapper.insert(newSession);
        return newSession;
    }

    private void saveReferences(Long sessionId,
                                Long assistantMessageId,
                                List<RagAnswerReferenceResponse> references) {
        if (references == null || references.isEmpty()) {
            return;
        }

        for (int i = 0; i < references.size(); i++) {
            RagAnswerReferenceResponse referenceResponse = references.get(i);

            Integer referenceIndex = referenceResponse.getIndex();
            if (referenceIndex == null) {
                referenceIndex = i + 1;
            }

            QaReference reference = new QaReference()
                    .setMessageId(assistantMessageId)
                    .setSessionId(sessionId)
                    .setReferenceIndex(referenceIndex)
                    .setChunkId(referenceResponse.getChunkId())
                    .setDocumentId(referenceResponse.getDocumentId())
                    .setSpaceId(referenceResponse.getSpaceId())
                    .setChunkIndex(referenceResponse.getChunkIndex())
                    .setScore(referenceResponse.getScore())
                    .setContentPreview(referenceResponse.getContentPreview());

            qaReferenceMapper.insert(reference);
        }
    }

    private List<QaReferenceResponse> listReferencesByAssistantMessageId(Long messageId) {
        LambdaQueryWrapper<QaReference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaReference::getMessageId, messageId)
                .orderByAsc(QaReference::getReferenceIndex)
                .orderByAsc(QaReference::getId);

        List<QaReference> references = qaReferenceMapper.selectList(wrapper);
        List<QaReferenceResponse> responses = new ArrayList<>();

        for (QaReference reference : references) {
            responses.add(toReferenceResponse(reference));
        }

        return responses;
    }

    private String buildSessionTitle(String question) {
        if (question == null) {
            return "Untitled session";
        }

        String title = question.trim();
        if (title.isEmpty()) {
            return "Untitled session";
        }

        if (title.length() > 50) {
            return title.substring(0, 50);
        }

        return title;
    }

    private QaSessionResponse toSessionResponse(QaSession session) {
        return new QaSessionResponse()
                .setId(session.getId())
                .setSpaceId(session.getSpaceId())
                .setTitle(session.getTitle())
                .setStatus(session.getStatus())
                .setCreatedAt(session.getCreatedAt())
                .setUpdatedAt(session.getUpdatedAt());
    }

    private QaMessageResponse toMessageResponse(QaMessage message, List<QaReferenceResponse> references) {
        return new QaMessageResponse()
                .setId(message.getId())
                .setSessionId(message.getSessionId())
                .setSpaceId(message.getSpaceId())
                .setRole(message.getRole())
                .setContent(message.getContent())
                .setCreatedAt(message.getCreatedAt())
                .setReferences(references);
    }

    private QaReferenceResponse toReferenceResponse(QaReference reference) {
        return new QaReferenceResponse()
                .setId(reference.getId())
                .setMessageId(reference.getMessageId())
                .setSessionId(reference.getSessionId())
                .setReferenceIndex(reference.getReferenceIndex())
                .setChunkId(reference.getChunkId())
                .setDocumentId(reference.getDocumentId())
                .setSpaceId(reference.getSpaceId())
                .setChunkIndex(reference.getChunkIndex())
                .setScore(reference.getScore())
                .setContentPreview(reference.getContentPreview())
                .setCreatedAt(reference.getCreatedAt());
    }
}