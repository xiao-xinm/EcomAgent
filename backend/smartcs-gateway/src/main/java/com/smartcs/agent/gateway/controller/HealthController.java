package com.smartcs.agent.gateway.controller;

import com.smartcs.agent.common.domain.ServiceHealth;
import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.util.TraceIds;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight readiness endpoint for local development and smoke checks.
 */
@RestController
public class HealthController {

    private final String applicationName;
    private final String activeProfile;

    public HealthController(
            @Value("${spring.application.name:smartcs-gateway}") String applicationName,
            @Value("${spring.profiles.active:default}") String activeProfile) {
        this.applicationName = applicationName;
        this.activeProfile = activeProfile;
    }

    @GetMapping("/api/health")
    public ApiResponse<ServiceHealth> health() {
        String traceId = TraceIds.newTraceId();
        ServiceHealth health = new ServiceHealth(
                applicationName,
                "UP",
                "1.0.0-SNAPSHOT",
                activeProfile,
                Map.of("module", "gateway"),
                Instant.now());
        return ApiResponse.success(health, traceId);
    }
}
