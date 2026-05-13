package com.nuaa.ragagent.service.impl;

import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.request.AskRequest;
import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.AskResponse;
import com.nuaa.ragagent.response.QaHistorySaveResult;
import com.nuaa.ragagent.response.RagAnswerReferenceResponse;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.service.QaHistoryService;
import com.nuaa.ragagent.service.RagQaService;
import com.nuaa.ragagent.service.RagRetrievalService;
import com.nuaa.ragagent.util.RagPromptBuilder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagQaServiceImpl implements RagQaService {

    private static final int DEFAULT_TOP_K = 5;

    private static final int MAX_TOP_K = 10;

    private static final int CONTENT_PREVIEW_LENGTH = 300;

    private final RagRetrievalService ragRetrievalService;

    private final ChatClient chatClient;

    private final RagPromptBuilder ragPromptBuilder;

    private final QaHistoryService qaHistoryService;

    public RagQaServiceImpl(RagRetrievalService ragRetrievalService,
                            ChatClient chatClient,
                            RagPromptBuilder ragPromptBuilder,
                            QaHistoryService qaHistoryService) {
        this.ragRetrievalService = ragRetrievalService;
        this.chatClient = chatClient;
        this.ragPromptBuilder = ragPromptBuilder;
        this.qaHistoryService = qaHistoryService;
    }

    @Override
    public AskResponse ask(AskRequest request) {
        validateRequest(request);

        int topK = normalizeTopK(request.getTopK());

        SearchChunksRequest searchRequest = buildSearchRequest(request, topK);

        List<SearchChunkResponse> chunks = ragRetrievalService.search(searchRequest);

        AskResponse response;

        if (chunks == null || chunks.isEmpty()) {
            response = buildNoReferenceResponse(request);
        } else {
            String prompt = ragPromptBuilder.buildPrompt(request.getQuestion(), chunks);

            String answer = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            List<RagAnswerReferenceResponse> references = buildReferences(chunks);

            response = new AskResponse()
                    .setSpaceId(request.getSpaceId())
                    .setQuestion(request.getQuestion())
                    .setAnswer(answer)
                    .setReferenceCount(references.size())
                    .setReferences(references);
        }

        QaHistorySaveResult historySaveResult = qaHistoryService.saveAskHistory(request, response);

        response.setSessionId(historySaveResult.getSessionId())
                .setUserMessageId(historySaveResult.getUserMessageId())
                .setAssistantMessageId(historySaveResult.getAssistantMessageId());

        return response;
    }

    private SearchChunksRequest buildSearchRequest(AskRequest request, int topK) {
        SearchChunksRequest searchRequest = new SearchChunksRequest();

        searchRequest.setSpaceId(request.getSpaceId());
        searchRequest.setQuery(request.getQuestion());
        searchRequest.setTopK(topK);

        searchRequest.setRetrievalMode(request.getRetrievalMode());
        searchRequest.setCandidateK(request.getCandidateK());
        searchRequest.setVectorWeight(request.getVectorWeight());
        searchRequest.setKeywordWeight(request.getKeywordWeight());

        return searchRequest;
    }

    private void validateRequest(AskRequest request) {
        if (request == null) {
            throw new BusinessException("AskRequest cannot be null");
        }

        if (request.getSpaceId() == null) {
            throw new BusinessException("spaceId cannot be null");
        }

        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new BusinessException("question cannot be blank");
        }
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }

        if (topK <= 0) {
            return DEFAULT_TOP_K;
        }

        return Math.min(topK, MAX_TOP_K);
    }

    private AskResponse buildNoReferenceResponse(AskRequest request) {
        AskResponse response = new AskResponse();
        response.setSpaceId(request.getSpaceId());
        response.setQuestion(request.getQuestion());
        response.setAnswer("当前知识库中没有检索到足够相关的内容，因此无法基于已有知识库给出可靠回答。");
        response.setReferenceCount(0);
        response.setReferences(new ArrayList<>());
        return response;
    }

    private List<RagAnswerReferenceResponse> buildReferences(List<SearchChunkResponse> chunks) {
        List<RagAnswerReferenceResponse> references = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            SearchChunkResponse chunk = chunks.get(i);

            RagAnswerReferenceResponse reference = new RagAnswerReferenceResponse();
            reference.setIndex(i + 1);
            reference.setChunkId(chunk.getChunkId());
            reference.setDocumentId(chunk.getDocumentId());
            reference.setSpaceId(chunk.getSpaceId());
            reference.setChunkIndex(chunk.getChunkIndex());
            reference.setScore(chunk.getScore());
            reference.setContentPreview(buildContentPreview(chunk.getContent()));

            references.add(reference);
        }

        return references;
    }

    private String buildContentPreview(String content) {
        if (content == null) {
            return "";
        }

        String normalized = content.replace("\r", " ").replace("\n", " ").trim();

        if (normalized.length() <= CONTENT_PREVIEW_LENGTH) {
            return normalized;
        }

        return normalized.substring(0, CONTENT_PREVIEW_LENGTH) + "...";
    }
}