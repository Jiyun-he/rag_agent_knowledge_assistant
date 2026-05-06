package com.nuaa.ragagent.service;

import com.nuaa.ragagent.request.AskRequest;
import com.nuaa.ragagent.response.AskResponse;

public interface RagQaService {

    AskResponse ask(AskRequest request);
}