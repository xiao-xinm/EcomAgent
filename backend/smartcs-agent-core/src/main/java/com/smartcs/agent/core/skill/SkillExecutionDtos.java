package com.smartcs.agent.core.skill;

import com.smartcs.agent.common.enums.RiskLevel;
import com.smartcs.agent.common.enums.RouteDecision;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Local DTOs used by Agent Core to call Skill Engine.
 */
public final class SkillExecutionDtos {

    private SkillExecutionDtos() {
    }

    public record SkillExecutionRequest(
            String traceId,
            String sessionId,
            String messageId,
            String userId,
            String intent,
            String skillId,
            RiskLevel riskLevel,
            RouteDecision routeDecision,
            Map<String, Object> parameters,
            Map<String, Object> context
    ) {
    }

    public record SkillExecutionResult(
            String executionId,
            String traceId,
            String sessionId,
            String messageId,
            String skillId,
            String skillName,
            String intent,
            RiskLevel riskLevel,
            RouteDecision routeDecision,
            String status,
            Map<String, Object> response,
            List<Map<String, Object>> stepResults,
            String message,
            Instant startedAt,
            Instant finishedAt
    ) {
    }
}
