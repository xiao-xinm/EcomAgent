package com.smartcs.agent.workbench.controller;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.dto.PageResult;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.common.auth.AuthHeaders;
import com.smartcs.agent.common.auth.AuthRoles;
import com.smartcs.agent.common.auth.AuthenticatedPrincipal;
import com.smartcs.agent.common.auth.PrincipalType;
import com.smartcs.agent.common.enums.ErrorCode;
import com.smartcs.agent.workbench.auth.WorkbenchIdentityResolver;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.ActionLogView;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.ActionResult;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.ApprovalDecisionRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.CurrentOperatorView;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.InternalNoteRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.OperatorActionRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TakeoverFinishRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TakeoverMessageRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TicketDetail;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TicketStatsView;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TicketSummary;
import com.smartcs.agent.workbench.ticket.WorkbenchOperationException;
import com.smartcs.agent.workbench.ticket.WorkbenchTicketService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workbench APIs for the first human-review and takeover closed loop.
 */
@RestController
public class WorkbenchTicketController {

    private static final String APPLICATION_JSON_UTF8 = "application/json;charset=UTF-8";

    private final WorkbenchTicketService ticketService;
    private final WorkbenchIdentityResolver identityResolver;

    public WorkbenchTicketController(WorkbenchTicketService ticketService, WorkbenchIdentityResolver identityResolver) {
        this.ticketService = ticketService;
        this.identityResolver = identityResolver;
    }

    @GetMapping(value = "/api/workbench/me", produces = APPLICATION_JSON_UTF8)
    public ApiResponse<CurrentOperatorView> getCurrentOperator(@RequestHeader HttpHeaders headers) {
        String traceId = TraceIds.newTraceId();
        AuthenticatedPrincipal principal = requireWorkbenchOperator(headers, null);
        return ApiResponse.success(toCurrentOperatorView(principal), traceId);
    }

    @GetMapping(value = "/api/workbench/tickets", produces = APPLICATION_JSON_UTF8)
    public ApiResponse<PageResult<TicketSummary>> listTickets(
            @RequestHeader HttpHeaders headers,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String routeDecision,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String intent,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String assignedAgent,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String createdAtFrom,
            @RequestParam(required = false) String createdAtTo,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        String traceId = TraceIds.newTraceId();
        requireWorkbenchOperator(headers, null);
        PageResult<TicketSummary> result = ticketService.listTickets(
                status,
                routeDecision,
                riskLevel,
                intent,
                priority,
                assignedAgent,
                keyword,
                createdAtFrom,
                createdAtTo,
                pageNo,
                pageSize);
        return ApiResponse.success(result, traceId);
    }

    @GetMapping(value = "/api/workbench/tickets/stats", produces = APPLICATION_JSON_UTF8)
    public ApiResponse<TicketStatsView> getTicketStats(@RequestHeader HttpHeaders headers) {
        String traceId = TraceIds.newTraceId();
        requireWorkbenchOperator(headers, null);
        return ApiResponse.success(ticketService.getTicketStats(), traceId);
    }

    @GetMapping(value = "/api/workbench/tickets/{ticketId}", produces = APPLICATION_JSON_UTF8)
    public ApiResponse<TicketDetail> getTicketDetail(
            @PathVariable String ticketId,
            @RequestHeader HttpHeaders headers) {
        String traceId = TraceIds.newTraceId();
        requireWorkbenchOperator(headers, null);
        return ApiResponse.success(ticketService.getTicketDetail(ticketId), traceId);
    }

    @GetMapping(value = "/api/workbench/tickets/{ticketId}/actions", produces = APPLICATION_JSON_UTF8)
    public ApiResponse<List<ActionLogView>> listActions(
            @PathVariable String ticketId,
            @RequestHeader HttpHeaders headers) {
        String traceId = TraceIds.newTraceId();
        requireWorkbenchOperator(headers, null);
        return ApiResponse.success(ticketService.listActions(ticketId), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/notes",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> addInternalNote(
            @PathVariable String ticketId,
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody InternalNoteRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.addInternalNote(ticketId, withOperator(request, headers)), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/claim",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> claim(
            @PathVariable String ticketId,
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody OperatorActionRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.claim(ticketId, withOperator(request, headers)), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/approval/approve",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> approve(
            @PathVariable String ticketId,
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.approve(ticketId, withOperator(request, headers)), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/approval/reject",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> reject(
            @PathVariable String ticketId,
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.reject(ticketId, withOperator(request, headers)), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/approval/request-materials",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> requestMaterials(
            @PathVariable String ticketId,
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.requestMaterials(ticketId, withOperator(request, headers)), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/approval/transfer-takeover",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> transferToTakeover(
            @PathVariable String ticketId,
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.transferToTakeover(ticketId, withOperator(request, headers)), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/takeover/start",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> startTakeover(
            @PathVariable String ticketId,
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody OperatorActionRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.startTakeover(ticketId, withOperator(request, headers)), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/takeover/messages",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> sendTakeoverMessage(
            @PathVariable String ticketId,
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody TakeoverMessageRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.sendTakeoverMessage(ticketId, withOperator(request, headers)), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/takeover/finish",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> finishTakeover(
            @PathVariable String ticketId,
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody TakeoverFinishRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.finishTakeover(ticketId, withOperator(request, headers)), traceId);
    }

    private CurrentOperatorView toCurrentOperatorView(AuthenticatedPrincipal principal) {
        List<String> roles = new ArrayList<>(principal.roles());
        Collections.sort(roles);
        return new CurrentOperatorView(
                principal.principalId(),
                principal.principalType().name(),
                roles,
                principal.authSource().name());
    }

    private OperatorActionRequest withOperator(OperatorActionRequest request, HttpHeaders headers) {
        AuthenticatedPrincipal principal = requireWorkbenchOperator(headers, request.operatorId());
        return new OperatorActionRequest(principal.principalId(), request.comment(), request.payload());
    }

    private ApprovalDecisionRequest withOperator(ApprovalDecisionRequest request, HttpHeaders headers) {
        AuthenticatedPrincipal principal = requireWorkbenchOperator(headers, request.operatorId());
        return new ApprovalDecisionRequest(
                principal.principalId(),
                request.comment(),
                request.decisionType(),
                request.result());
    }

    private TakeoverFinishRequest withOperator(TakeoverFinishRequest request, HttpHeaders headers) {
        AuthenticatedPrincipal principal = requireWorkbenchOperator(headers, request.operatorId());
        return new TakeoverFinishRequest(
                principal.principalId(),
                request.comment(),
                request.resolutionStatus(),
                request.result());
    }

    private TakeoverMessageRequest withOperator(TakeoverMessageRequest request, HttpHeaders headers) {
        AuthenticatedPrincipal principal = requireWorkbenchOperator(headers, request.operatorId());
        return new TakeoverMessageRequest(principal.principalId(), request.content(), request.payload());
    }

    private InternalNoteRequest withOperator(InternalNoteRequest request, HttpHeaders headers) {
        AuthenticatedPrincipal principal = requireWorkbenchOperator(headers, request.operatorId());
        return new InternalNoteRequest(principal.principalId(), request.comment(), request.payload());
    }

    private AuthenticatedPrincipal requireWorkbenchOperator(HttpHeaders headers, String legacyOperatorId) {
        rejectInvalidStandardPrincipal(headers);
        AuthenticatedPrincipal principal = identityResolver.resolveAgent(headers, legacyOperatorId);
        if (!principal.hasAnyRole(AuthRoles.AGENT, AuthRoles.SUPERVISOR, AuthRoles.ADMIN)) {
            throw forbidden("Current identity has no workbench role");
        }
        return principal;
    }

    private void rejectInvalidStandardPrincipal(HttpHeaders headers) {
        String principalId = firstHeader(headers, AuthHeaders.PRINCIPAL_ID);
        String principalType = firstHeader(headers, AuthHeaders.PRINCIPAL_TYPE);
        if (!hasText(principalId) && !hasText(principalType)) {
            return;
        }
        Optional<PrincipalType> parsedType = PrincipalType.parse(principalType);
        if (parsedType.isEmpty() || !isWorkbenchPrincipal(parsedType.get())) {
            throw forbidden("Current identity is not a workbench operator");
        }
    }

    private boolean isWorkbenchPrincipal(PrincipalType principalType) {
        return principalType == PrincipalType.AGENT
                || principalType == PrincipalType.SUPERVISOR
                || principalType == PrincipalType.ADMIN;
    }

    private String firstHeader(HttpHeaders headers, String name) {
        return headers == null ? null : headers.getFirst(name);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private WorkbenchOperationException forbidden(String message) {
        return new WorkbenchOperationException(ErrorCode.FORBIDDEN, message);
    }
}
