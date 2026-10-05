package com.nuaa.ragagent.controller;

import com.nuaa.ragagent.common.ApiResponse;
import com.nuaa.ragagent.entity.KbSpace;
import com.nuaa.ragagent.request.CreateSpaceRequest;
import com.nuaa.ragagent.request.UpdateSpaceRequest;
import com.nuaa.ragagent.service.KnowledgeSpaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author jiyunhe
 */

@RestController
@RequestMapping("/api/spaces")
public class KnowledgeSpaceController {

    private final KnowledgeSpaceService knowledgeSpaceService;

    public KnowledgeSpaceController(KnowledgeSpaceService knowledgeSpaceService) {
        this.knowledgeSpaceService = knowledgeSpaceService;
    }

    @PostMapping
    public ApiResponse<KbSpace> create(@Valid @RequestBody CreateSpaceRequest request) {
        return ApiResponse.success(knowledgeSpaceService.create(request));
    }

    @GetMapping
    public ApiResponse<List<KbSpace>> list() {
        return ApiResponse.success(knowledgeSpaceService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<KbSpace> getById(@PathVariable Long id) {
        return ApiResponse.success(knowledgeSpaceService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<KbSpace> update(@PathVariable Long id,
                                       @Valid @RequestBody UpdateSpaceRequest request) {
        return ApiResponse.success(knowledgeSpaceService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        return ApiResponse.success(knowledgeSpaceService.delete(id));
    }
}
