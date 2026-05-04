package com.nuaa.ragagent.controller;

import com.nuaa.ragagent.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health/ping")
    public ApiResponse<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "pong");
        data.put("time", LocalDateTime.now());
        return ApiResponse.success(data);
    }
}