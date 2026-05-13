package com.nuaa.ragagent.service;

import com.nuaa.ragagent.request.SearchChunksRequest;
import com.nuaa.ragagent.response.SearchChunkResponse;

import java.util.List;

public interface RagRetrievalService {

    List<SearchChunkResponse> search(SearchChunksRequest request);
}