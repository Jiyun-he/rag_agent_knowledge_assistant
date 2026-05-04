package com.nuaa.ragagent.service;

import com.nuaa.ragagent.entity.KbSpace;
import com.nuaa.ragagent.request.CreateSpaceRequest;
import com.nuaa.ragagent.request.UpdateSpaceRequest;

import java.util.List;

public interface KnowledgeSpaceService {

    KbSpace create(CreateSpaceRequest request);

    List<KbSpace> list();

    KbSpace getById(Long id);

    KbSpace update(Long id, UpdateSpaceRequest request);

    Boolean delete(Long id);
}