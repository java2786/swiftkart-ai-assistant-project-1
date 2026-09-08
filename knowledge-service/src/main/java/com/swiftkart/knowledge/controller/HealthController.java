package com.swiftkart.knowledge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
@Tag(name = "Health", description = "Service Health APIs")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Check service health", description = "Returns UP status if the knowledge microservice is healthy")
    public Map<String, String> health() {
        return Map.of(
                "service", "knowledge-service",
                "status", "UP");
    }
}
