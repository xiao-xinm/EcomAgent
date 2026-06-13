package com.smartcs.agent.workbench.ticket;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * API contracts for the first workbench closed loop.
 */
public final class WorkbenchDtos {

    private WorkbenchDtos() {
    }

    public record TicketSummary(
            String ticketId,
            String traceId,
            String sessionId,
            String userId,
            String intent,
            String riskLevel,
            String routeDecision,
            String status,
            String priority,
            String assignedAgent,
            String reason,
            Instant slaDeadline,
            Instant createdAt,
            Instant updatedAt,
            String approvalId,
            String approvalType,
            String approvalStatus,
            String takeoverId,
            String takeoverStatus
    ) {
    }

    public record WorkOrderView(
            String ticketId,
            String traceId,
            String sessionId,
            String userId,
            String intent,
            String riskLevel,
            String routeDecision,
            String status,
            String priority,
            String assignedAgent,
            String reason,
            Map<String, Object> contextSnapshot,
            Map<String, Object> resolution,
            Instant slaDeadline,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt
    ) {
    }

    public record ApprovalTaskView(
            String approvalId,
            String ticketId,
            String traceId,
            String sessionId,
            String userId,
            String intent,
            String approvalType,
            String riskLevel,
            String routeDecision,
            String status,
            String priority,
            String assignedReviewer,
            String riskReason,
            Map<String, Object> requestPayload,
            Map<String, Object> contextSnapshot,
            Map<String, Object> approvalResult,
            Instant expireAt,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt
    ) {
    }

    public record HumanTakeoverView(
            String takeoverId,
            String ticketId,
            String traceId,
            String sessionId,
            String userId,
            String triggerSource,
            String status,
            String priority,
            String assignedAgent,
            String reason,
            Map<String, Object> contextSnapshot,
            Instant startedAt,
            Instant endedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record MessageView(
            String messageId,
            String traceId,
            String sessionId,
            String userId,
            String role,
            String messageType,
            String content,
            List<Object> quickActions,
            String intent,
            String riskLevel,
            String routeDecision,
            Map<String, Object> metadata,
            Instant createdAt
    ) {
    }

    public record ActionLogView(
            String actionId,
            String source,
            String ticketId,
            String traceId,
            String operatorId,
            String actionType,
            String beforeStatus,
            String afterStatus,
            String comment,
            Map<String, Object> actionData,
            Instant createdAt
    ) {
    }

    public record TicketDetail(
            WorkOrderView ticket,
            ApprovalTaskView approval,
            HumanTakeoverView takeover,
            List<MessageView> messages,
            List<ActionLogView> actions
    ) {
    }

    public record OperatorActionRequest(
            @NotBlank String operatorId,
            String comment,
            Map<String, Object> payload
    ) {
    }

    public record ApprovalDecisionRequest(
            @NotBlank String operatorId,
            String comment,
            Map<String, Object> result
    ) {
    }

    public record TakeoverFinishRequest(
            @NotBlank String operatorId,
            String comment,
            String resolutionStatus,
            Map<String, Object> result
    ) {
    }

    public record ActionResult(
            String ticketId,
            String workOrderStatus,
            String approvalStatus,
            String takeoverStatus,
            String message
    ) {
    }
}
