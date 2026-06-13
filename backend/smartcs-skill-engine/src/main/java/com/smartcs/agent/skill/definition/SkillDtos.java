package com.smartcs.agent.skill.definition;

import com.smartcs.agent.common.enums.RiskLevel;
import com.smartcs.agent.common.enums.RouteDecision;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * API DTOs for skill definition and execution.
 */
public final class SkillDtos {

    private SkillDtos() {
    }

    public record SkillExecuteRequest(
            String traceId,
            @NotBlank String sessionId,
            String messageId,
            @NotBlank String userId,
            String intent,
            String skillId,
            RiskLevel riskLevel,
            RouteDecision routeDecision,
            Map<String, Object> parameters,
            Map<String, Object> context
    ) {
    }

    public record SkillExecuteResult(
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
            List<SkillStepResult> stepResults,
            String message,
            Instant startedAt,
            Instant finishedAt
    ) {
    }

    public record SkillDefinitionView(
            String skillId,
            String skillName,
            String intent,
            RiskLevel baseRiskLevel,
            String executionType,
            boolean enabled,
            String status,
            String description,
            Object preConditions,
            Object retryPolicy,
            int timeoutMs,
            String owner,
            String version,
            Instant createdAt,
            Instant updatedAt,
            List<SkillSlotView> slots,
            List<SkillStepView> steps
    ) {
    }

    public record SkillSlotView(
            long slotId,
            String skillId,
            String slotName,
            String slotType,
            boolean required,
            Object sourcePriority,
            Object enumValues,
            String validationRule,
            String clarificationTemplate,
            Object defaultValue,
            int displayOrder
    ) {
    }

    public record SkillStepView(
            long stepId,
            String skillId,
            int stepNo,
            String stepName,
            String apiName,
            String httpMethod,
            String endpoint,
            Object paramsMapping,
            Object headersMapping,
            Object bodyTemplate,
            Object resultMapping,
            boolean required,
            String rollbackEndpoint,
            Object rollbackMapping,
            int timeoutMs,
            Object retryPolicy
    ) {
    }

    public record SkillStepResult(
            int stepNo,
            String stepName,
            String apiName,
            String status,
            Map<String, Object> request,
            Map<String, Object> response,
            String message
    ) {
    }
}
