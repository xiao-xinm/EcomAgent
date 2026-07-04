package com.smartcs.agent.workbench.controller;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.dto.PageResult;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.ActionLogView;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.ActionResult;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.ApprovalDecisionRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.InternalNoteRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.OperatorActionRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TakeoverFinishRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TakeoverMessageRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TicketDetail;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TicketStatsView;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TicketSummary;
import com.smartcs.agent.workbench.ticket.WorkbenchTicketService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
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

    public WorkbenchTicketController(WorkbenchTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping(value = "/api/workbench/tickets", produces = APPLICATION_JSON_UTF8)
    public ApiResponse<PageResult<TicketSummary>> listTickets(
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
    public ApiResponse<TicketStatsView> getTicketStats() {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.getTicketStats(), traceId);
    }

    @GetMapping(value = "/api/workbench/tickets/{ticketId}", produces = APPLICATION_JSON_UTF8)
    public ApiResponse<TicketDetail> getTicketDetail(@PathVariable String ticketId) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.getTicketDetail(ticketId), traceId);
    }

    @GetMapping(value = "/api/workbench/tickets/{ticketId}/actions", produces = APPLICATION_JSON_UTF8)
    public ApiResponse<List<ActionLogView>> listActions(@PathVariable String ticketId) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.listActions(ticketId), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/notes",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> addInternalNote(
            @PathVariable String ticketId,
            @Valid @RequestBody InternalNoteRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.addInternalNote(ticketId, request), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/claim",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> claim(
            @PathVariable String ticketId,
            @Valid @RequestBody OperatorActionRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.claim(ticketId, request), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/approval/approve",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> approve(
            @PathVariable String ticketId,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.approve(ticketId, request), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/approval/reject",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> reject(
            @PathVariable String ticketId,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.reject(ticketId, request), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/takeover/start",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> startTakeover(
            @PathVariable String ticketId,
            @Valid @RequestBody OperatorActionRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.startTakeover(ticketId, request), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/takeover/messages",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> sendTakeoverMessage(
            @PathVariable String ticketId,
            @Valid @RequestBody TakeoverMessageRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.sendTakeoverMessage(ticketId, request), traceId);
    }

    @PostMapping(
            value = "/api/workbench/tickets/{ticketId}/takeover/finish",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ActionResult> finishTakeover(
            @PathVariable String ticketId,
            @Valid @RequestBody TakeoverFinishRequest request) {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(ticketService.finishTakeover(ticketId, request), traceId);
    }
}
