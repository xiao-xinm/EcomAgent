package com.smartcs.agent.common.domain;

import com.smartcs.agent.common.enums.RiskLevel;
import com.smartcs.agent.common.enums.RouteDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Risk routing result shared by agent core, skill engine, and workbench.
 */
public record RiskDecision(
        @NotBlank String traceId,
        @NotBlank String sessionId,
        @NotNull RiskLevel riskLevel,
        @NotNull RouteDecision routeDecision,
        String reasonCode,
        String reason,
        List<String> matchedRuleIds,
        Map<String, Object> details,
        @NotNull Instant decidedAt
) {
}
