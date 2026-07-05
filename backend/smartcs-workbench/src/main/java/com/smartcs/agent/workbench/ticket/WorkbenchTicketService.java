package com.smartcs.agent.workbench.ticket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.common.dto.PageResult;
import com.smartcs.agent.common.enums.ErrorCode;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.ActionLogView;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.ActionResult;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.ApprovalDecisionRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.ApprovalTaskView;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.HumanTakeoverView;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.InternalNoteRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.MessageView;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.OperatorActionRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TakeoverFinishRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TakeoverMessageRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TicketDetail;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TicketStatsView;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TicketSummary;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.WorkOrderView;
import com.smartcs.agent.workbench.notification.NotificationEventClient;
import com.smartcs.agent.workbench.notification.NotificationEventDtos.NotificationEventRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 坐席工作台服务：负责工单领取、审批、人工接管和操作审计。
 */
@Service
public class WorkbenchTicketService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkbenchTicketService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };
    private static final List<String> TERMINAL_WORK_ORDER_STATUSES =
            List.of("APPROVED", "REJECTED", "RESOLVED", "CLOSED");
    private static final List<String> OPEN_APPROVAL_STATUSES = List.of("PENDING", "CLAIMED");
    private static final List<String> TERMINAL_TAKEOVER_STATUSES = List.of("RESOLVED", "CANCELLED");
    private static final List<String> APPROVAL_DECISION_TYPES =
            List.of("APPROVED", "REJECTED", "REQUEST_MATERIALS", "TRANSFER_TAKEOVER");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationEventClient notificationEventClient;

    public WorkbenchTicketService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            NotificationEventClient notificationEventClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.notificationEventClient = notificationEventClient;
    }

    public PageResult<TicketSummary> listTickets(
            String status,
            String routeDecision,
            String riskLevel,
            String intent,
            String priority,
            String assignedAgent,
            String keyword,
            String createdAtFrom,
            String createdAtTo,
            int pageNo,
            int pageSize) {
        int normalizedPageNo = Math.max(pageNo, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), 200);
        SqlFilter filter = buildTicketFilter(
                status,
                routeDecision,
                riskLevel,
                intent,
                priority,
                assignedAgent,
                keyword,
                createdAtFrom,
                createdAtTo);

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order w " + filter.whereSql(),
                Long.class,
                filter.args().toArray());

        List<Object> args = new ArrayList<>(filter.args());
        args.add(normalizedPageSize);
        args.add((normalizedPageNo - 1) * normalizedPageSize);
        List<TicketSummary> records = jdbcTemplate.query(
                """
                SELECT
                    w.ticket_id, w.trace_id, w.session_id, w.user_id, w.intent, w.risk_level,
                    w.route_decision, w.status, w.priority, w.assigned_agent, w.reason,
                    w.sla_deadline, w.created_at, w.updated_at,
                    a.approval_id, a.approval_type, a.status AS approval_status,
                    h.takeover_id, h.status AS takeover_status
                FROM work_order w
                LEFT JOIN approval_task a ON a.ticket_id = w.ticket_id
                LEFT JOIN human_takeover h ON h.ticket_id = w.ticket_id
                """ + filter.whereSql() + """

                ORDER BY w.created_at DESC
                LIMIT ? OFFSET ?
                """,
                this::mapTicketSummary,
                args.toArray());

        long normalizedTotal = total == null ? 0 : total;
        LOGGER.info(
                "查询工单列表 status={} routeDecision={} riskLevel={} intent={} priority={} assignedAgent={} "
                        + "createdAtFrom={} createdAtTo={} keywordPresent={} pageNo={} pageSize={} total={} returned={}",
                status,
                routeDecision,
                riskLevel,
                intent,
                priority,
                assignedAgent,
                createdAtFrom,
                createdAtTo,
                hasText(keyword),
                normalizedPageNo,
                normalizedPageSize,
                normalizedTotal,
                records.size());
        return new PageResult<>(records, normalizedTotal, normalizedPageNo, normalizedPageSize);
    }

    public TicketStatsView getTicketStats() {
        TicketStatsView stats = jdbcTemplate.queryForObject(
                """
                SELECT
                    COUNT(*) AS total,
                    SUM(CASE WHEN status IN ('PENDING', 'ASSIGNED', 'ESCALATED') THEN 1 ELSE 0 END) AS pending,
                    SUM(CASE WHEN status = 'PROCESSING' THEN 1 ELSE 0 END) AS processing,
                    SUM(CASE WHEN status IN ('APPROVED', 'REJECTED', 'RESOLVED', 'CLOSED') THEN 1 ELSE 0 END) AS completed,
                    SUM(CASE
                        WHEN sla_deadline IS NOT NULL
                         AND sla_deadline < CURRENT_TIMESTAMP(3)
                         AND status NOT IN ('APPROVED', 'REJECTED', 'RESOLVED', 'CLOSED')
                        THEN 1 ELSE 0
                    END) AS overdue_risk
                FROM work_order
                """,
                (rs, rowNum) -> new TicketStatsView(
                        rs.getLong("total"),
                        rs.getLong("pending"),
                        rs.getLong("processing"),
                        rs.getLong("completed"),
                        rs.getLong("overdue_risk")));
        TicketStatsView normalized = stats == null ? new TicketStatsView(0, 0, 0, 0, 0) : stats;
        LOGGER.info(
                "查询工单统计 total={} pending={} processing={} completed={} overdueRisk={}",
                normalized.total(),
                normalized.pending(),
                normalized.processing(),
                normalized.completed(),
                normalized.overdueRisk());
        return normalized;
    }

    public TicketDetail getTicketDetail(String ticketId) {
        WorkOrderView ticket = requireTicket(ticketId);
        LOGGER.info(
                "查询工单详情 ticketId={} traceId={} sessionId={} status={} routeDecision={}",
                ticket.ticketId(),
                ticket.traceId(),
                ticket.sessionId(),
                ticket.status(),
                ticket.routeDecision());
        return new TicketDetail(
                ticket,
                findApproval(ticketId).orElse(null),
                findTakeover(ticketId).orElse(null),
                listMessages(ticket.sessionId()),
                listActions(ticketId));
    }

    public List<ActionLogView> listActions(String ticketId) {
        requireTicket(ticketId);
        List<ActionLogView> actions = jdbcTemplate.query(
                """
                SELECT
                    action_id,
                    'WORK_ORDER' AS source,
                    ticket_id,
                    trace_id,
                    operator_id,
                    action_type,
                    NULL AS before_status,
                    NULL AS after_status,
                    comment,
                    action_data,
                    created_at
                FROM work_order_action
                WHERE ticket_id = ?
                UNION ALL
                SELECT
                    action_id,
                    'APPROVAL' AS source,
                    ticket_id,
                    trace_id,
                    operator_id,
                    action_type,
                    before_status,
                    after_status,
                    comment,
                    action_data,
                    created_at
                FROM approval_action
                WHERE ticket_id = ?
                ORDER BY created_at ASC
                """,
                this::mapActionLog,
                ticketId,
                ticketId);
        LOGGER.info("查询工单操作日志 ticketId={} count={}", ticketId, actions.size());
        return actions;
    }

    @Transactional
    public ActionResult addInternalNote(String ticketId, InternalNoteRequest request) {
        WorkOrderView ticket = requireTicket(ticketId);
        String note = request.comment().trim();
        LOGGER.info(
                "坐席添加内部备注 ticketId={} traceId={} operatorId={} commentLength={}",
                ticketId,
                ticket.traceId(),
                request.operatorId(),
                note.length());

        // 内部备注只写入坐席侧操作记录和审计日志，不回写用户会话消息，避免用户端误以为有新的客服回复。
        Map<String, Object> noteData = data(
                "noteType", "INTERNAL",
                "payload", request.payload());
        insertWorkOrderAction(ticketId, ticket.traceId(), request.operatorId(), "INTERNAL_NOTE", note, noteData);
        insertAudit(ticket, request.operatorId(), "WORK_ORDER_INTERNAL_NOTE", data(
                "comment", note,
                "payload", request.payload()));

        ActionResult result = currentResult(ticketId, "内部备注已记录");
        logActionResult("添加内部备注完成", result, request.operatorId());
        return result;
    }

    @Transactional
    public ActionResult claim(String ticketId, OperatorActionRequest request) {
        WorkOrderView ticket = requireTicket(ticketId);
        rejectTerminalWorkOrder(ticket.status());
        LOGGER.info(
                "坐席领取工单开始 ticketId={} traceId={} operatorId={} beforeStatus={}",
                ticketId,
                ticket.traceId(),
                request.operatorId(),
                ticket.status());

        String beforeStatus = ticket.status();
        int updated = jdbcTemplate.update(
                """
                UPDATE work_order
                SET status = 'PROCESSING', assigned_agent = ?
                WHERE ticket_id = ? AND status IN ('PENDING', 'ASSIGNED', 'PROCESSING', 'ESCALATED')
                """,
                request.operatorId(),
                ticketId);
        if (updated == 0) {
            throw rejected("当前工单状态不允许领取");
        }

        findApproval(ticketId).ifPresent(approval -> claimApprovalIfOpen(approval, request));
        findTakeover(ticketId).ifPresent(takeover -> assignTakeoverIfOpen(takeover, request));
        insertWorkOrderAction(ticketId, ticket.traceId(), request.operatorId(), "ASSIGN", request.comment(),
                data("beforeStatus", beforeStatus, "afterStatus", "PROCESSING", "payload", request.payload()));
        insertAudit(ticket, request.operatorId(), "WORK_ORDER_CLAIMED",
                data("beforeStatus", beforeStatus, "afterStatus", "PROCESSING"));
        ActionResult result = currentResult(ticketId, "工单已领取");
        logActionResult("坐席领取工单完成", result, request.operatorId());
        return result;
    }

    @Transactional
    public ActionResult approve(String ticketId, ApprovalDecisionRequest request) {
        WorkOrderView ticket = requireTicket(ticketId);
        rejectTerminalWorkOrder(ticket.status());
        ApprovalTaskView approval = requireApproval(ticketId);
        requireOpenApproval(approval.status());
        LOGGER.info(
                "审批通过开始 ticketId={} traceId={} approvalId={} operatorId={} beforeWorkOrderStatus={} beforeApprovalStatus={}",
                ticketId,
                ticket.traceId(),
                approval.approvalId(),
                request.operatorId(),
                ticket.status(),
                approval.status());

        Map<String, Object> result = approvalConclusion(request, "APPROVED");
        jdbcTemplate.update(
                """
                UPDATE approval_task
                SET status = 'APPROVED',
                    assigned_reviewer = ?,
                    approval_result = CAST(? AS JSON),
                    completed_at = CURRENT_TIMESTAMP(3)
                WHERE approval_id = ?
                """,
                request.operatorId(),
                json(result),
                approval.approvalId());
        jdbcTemplate.update(
                """
                UPDATE work_order
                SET status = 'APPROVED',
                    assigned_agent = ?,
                    resolution = CAST(? AS JSON),
                    resolved_at = CURRENT_TIMESTAMP(3)
                WHERE ticket_id = ?
                """,
                request.operatorId(),
                json(result),
                ticketId);
        updateSessionState(ticket.sessionId(), "ACTIVE", "COMPLETED");
        insertApprovalAction(approval, request.operatorId(), "APPROVE", approval.status(), "APPROVED",
                request.comment(), result);
        insertWorkOrderAction(ticketId, ticket.traceId(), request.operatorId(), "APPROVE", request.comment(),
                data("beforeStatus", ticket.status(), "afterStatus", "APPROVED", "approvalId", approval.approvalId()));
        insertAudit(ticket, request.operatorId(), "APPROVAL_APPROVED",
                data("approvalId", approval.approvalId(), "beforeStatus", approval.status(), "afterStatus", "APPROVED"));
        String userMessage = approvalApprovedContent(approval);
        Map<String, Object> notificationData = data(
                "approvalId", approval.approvalId(),
                "approvalType", approval.approvalType(),
                "approvalStatus", "APPROVED",
                "workOrderStatus", "APPROVED");
        publishUserServiceEvent(ticket, "SYSTEM", userMessage, request.operatorId(),
                "APPROVAL_APPROVED",
                "审批通过通知",
                notificationData);
        ActionResult actionResult = currentResult(ticketId, "审批已通过");
        logActionResult("审批通过完成", actionResult, request.operatorId());
        return actionResult;
    }

    @Transactional
    public ActionResult reject(String ticketId, ApprovalDecisionRequest request) {
        WorkOrderView ticket = requireTicket(ticketId);
        rejectTerminalWorkOrder(ticket.status());
        ApprovalTaskView approval = requireApproval(ticketId);
        requireOpenApproval(approval.status());
        LOGGER.info(
                "审批驳回开始 ticketId={} traceId={} approvalId={} operatorId={} beforeWorkOrderStatus={} beforeApprovalStatus={}",
                ticketId,
                ticket.traceId(),
                approval.approvalId(),
                request.operatorId(),
                ticket.status(),
                approval.status());

        Map<String, Object> result = approvalConclusion(request, "REJECTED");
        jdbcTemplate.update(
                """
                UPDATE approval_task
                SET status = 'REJECTED',
                    assigned_reviewer = ?,
                    approval_result = CAST(? AS JSON),
                    completed_at = CURRENT_TIMESTAMP(3)
                WHERE approval_id = ?
                """,
                request.operatorId(),
                json(result),
                approval.approvalId());
        jdbcTemplate.update(
                """
                UPDATE work_order
                SET status = 'REJECTED',
                    assigned_agent = ?,
                    resolution = CAST(? AS JSON),
                    resolved_at = CURRENT_TIMESTAMP(3)
                WHERE ticket_id = ?
                """,
                request.operatorId(),
                json(result),
                ticketId);
        updateSessionState(ticket.sessionId(), "ACTIVE", "COMPLETED");
        insertApprovalAction(approval, request.operatorId(), "REJECT", approval.status(), "REJECTED",
                request.comment(), result);
        insertWorkOrderAction(ticketId, ticket.traceId(), request.operatorId(), "REJECT", request.comment(),
                data("beforeStatus", ticket.status(), "afterStatus", "REJECTED", "approvalId", approval.approvalId()));
        insertAudit(ticket, request.operatorId(), "APPROVAL_REJECTED",
                data("approvalId", approval.approvalId(), "beforeStatus", approval.status(), "afterStatus", "REJECTED"));
        String userMessage = approvalRejectedContent(approval);
        Map<String, Object> notificationData = data(
                "approvalId", approval.approvalId(),
                "approvalType", approval.approvalType(),
                "approvalStatus", "REJECTED",
                "workOrderStatus", "REJECTED");
        publishUserServiceEvent(ticket, "SYSTEM", userMessage, request.operatorId(),
                "APPROVAL_REJECTED",
                "审批驳回通知",
                notificationData);
        ActionResult actionResult = currentResult(ticketId, "审批已驳回");
        logActionResult("审批驳回完成", actionResult, request.operatorId());
        return actionResult;
    }

    @Transactional
    public ActionResult requestMaterials(String ticketId, ApprovalDecisionRequest request) {
        WorkOrderView ticket = requireTicket(ticketId);
        rejectTerminalWorkOrder(ticket.status());
        ApprovalTaskView approval = requireApproval(ticketId);
        requireOpenApproval(approval.status());
        LOGGER.info(
                "审批要求补充材料 ticketId={} traceId={} approvalId={} operatorId={} beforeApprovalStatus={}",
                ticketId,
                ticket.traceId(),
                approval.approvalId(),
                request.operatorId(),
                approval.status());

        Map<String, Object> result = approvalConclusion(request, "REQUEST_MATERIALS");
        jdbcTemplate.update(
                """
                UPDATE approval_task
                SET assigned_reviewer = ?,
                    approval_result = CAST(? AS JSON)
                WHERE approval_id = ?
                """,
                request.operatorId(),
                json(result),
                approval.approvalId());
        insertApprovalAction(approval, request.operatorId(), "COMMENT", approval.status(), approval.status(),
                request.comment(), result);
        insertAudit(ticket, request.operatorId(), "APPROVAL_MATERIALS_REQUESTED",
                data("approvalId", approval.approvalId(), "approvalStatus", approval.status(), "decisionType",
                        "REQUEST_MATERIALS"));

        String userMessage = approvalMaterialsRequestedContent(request);
        Map<String, Object> notificationData = data(
                "approvalId", approval.approvalId(),
                "approvalType", approval.approvalType(),
                "approvalStatus", approval.status(),
                "workOrderStatus", ticket.status(),
                "decisionType", "REQUEST_MATERIALS");
        publishUserServiceEvent(ticket, "SYSTEM", userMessage, request.operatorId(),
                "APPROVAL_MATERIALS_REQUESTED",
                "审批补充材料通知",
                notificationData);
        ActionResult actionResult = currentResult(ticketId, "已要求用户补充材料");
        logActionResult("审批要求补充材料完成", actionResult, request.operatorId());
        return actionResult;
    }

    @Transactional
    public ActionResult transferToTakeover(String ticketId, ApprovalDecisionRequest request) {
        WorkOrderView ticket = requireTicket(ticketId);
        rejectTerminalWorkOrder(ticket.status());
        ApprovalTaskView approval = requireApproval(ticketId);
        requireOpenApproval(approval.status());
        LOGGER.info(
                "审批转人工接管 ticketId={} traceId={} approvalId={} operatorId={} beforeWorkOrderStatus={} beforeApprovalStatus={}",
                ticketId,
                ticket.traceId(),
                approval.approvalId(),
                request.operatorId(),
                ticket.status(),
                approval.status());

        Map<String, Object> result = approvalConclusion(request, "TRANSFER_TAKEOVER");
        OperatorActionRequest takeoverRequest = new OperatorActionRequest(
                request.operatorId(),
                textOr(request.comment(), "审批转人工接管"),
                data("source", "approval", "approvalId", approval.approvalId(), "decisionType",
                        "TRANSFER_TAKEOVER", "result", request.result()));
        HumanTakeoverView takeover = findTakeover(ticketId)
                .orElseGet(() -> createTakeover(ticket, takeoverRequest));
        assignTakeoverIfOpen(takeover, takeoverRequest);

        jdbcTemplate.update(
                """
                UPDATE approval_task
                SET status = 'ESCALATED',
                    assigned_reviewer = ?,
                    approval_result = CAST(? AS JSON),
                    completed_at = CURRENT_TIMESTAMP(3)
                WHERE approval_id = ?
                """,
                request.operatorId(),
                json(result),
                approval.approvalId());
        jdbcTemplate.update(
                """
                UPDATE work_order
                SET status = 'ESCALATED',
                    assigned_agent = ?
                WHERE ticket_id = ?
                """,
                request.operatorId(),
                ticketId);

        HumanTakeoverView currentTakeover = findTakeover(ticketId).orElse(takeover);
        insertApprovalAction(approval, request.operatorId(), "ESCALATE", approval.status(), "ESCALATED",
                request.comment(), result);
        insertWorkOrderAction(ticketId, ticket.traceId(), request.operatorId(), "ESCALATE", request.comment(),
                data("beforeStatus", ticket.status(), "afterStatus", "ESCALATED", "approvalId",
                        approval.approvalId(), "takeoverId", currentTakeover.takeoverId()));
        insertAudit(ticket, request.operatorId(), "APPROVAL_TRANSFERRED_TO_TAKEOVER",
                data("approvalId", approval.approvalId(), "beforeStatus", approval.status(), "afterStatus",
                        "ESCALATED", "takeoverId", currentTakeover.takeoverId()));

        String userMessage = approvalTransferredToTakeoverContent();
        Map<String, Object> notificationData = data(
                "approvalId", approval.approvalId(),
                "approvalType", approval.approvalType(),
                "approvalStatus", "ESCALATED",
                "workOrderStatus", "ESCALATED",
                "takeoverId", currentTakeover.takeoverId(),
                "takeoverStatus", currentTakeover.status(),
                "decisionType", "TRANSFER_TAKEOVER");
        publishUserServiceEvent(ticket, "SYSTEM", userMessage, request.operatorId(),
                "APPROVAL_TRANSFERRED_TO_TAKEOVER",
                "审批转人工接管通知",
                notificationData);
        ActionResult actionResult = currentResult(ticketId, "已转人工接管");
        logActionResult("审批转人工接管完成", actionResult, request.operatorId());
        return actionResult;
    }

    @Transactional
    public ActionResult startTakeover(String ticketId, OperatorActionRequest request) {
        WorkOrderView ticket = requireTicket(ticketId);
        rejectTerminalWorkOrder(ticket.status());
        HumanTakeoverView takeover = findTakeover(ticketId)
                .orElseGet(() -> createTakeover(ticket, request));
        rejectTerminalTakeover(takeover.status());
        LOGGER.info(
                "人工接管开始 ticketId={} traceId={} takeoverId={} operatorId={} beforeWorkOrderStatus={} beforeTakeoverStatus={}",
                ticketId,
                ticket.traceId(),
                takeover.takeoverId(),
                request.operatorId(),
                ticket.status(),
                takeover.status());

        jdbcTemplate.update(
                """
                UPDATE human_takeover
                SET status = 'IN_PROGRESS',
                    assigned_agent = ?,
                    started_at = COALESCE(started_at, CURRENT_TIMESTAMP(3))
                WHERE takeover_id = ?
                """,
                request.operatorId(),
                takeover.takeoverId());
        jdbcTemplate.update(
                """
                UPDATE work_order
                SET status = 'PROCESSING',
                    assigned_agent = ?
                WHERE ticket_id = ?
                """,
                request.operatorId(),
                ticketId);
        updateSessionState(ticket.sessionId(), "HUMAN_TAKEOVER", "HUMAN_TAKEOVER");
        insertWorkOrderAction(ticketId, ticket.traceId(), request.operatorId(), "TAKEOVER", request.comment(),
                data("takeoverId", takeover.takeoverId(), "beforeStatus", takeover.status(), "afterStatus", "IN_PROGRESS",
                        "payload", request.payload()));
        insertAudit(ticket, request.operatorId(), "TAKEOVER_STARTED",
                data("takeoverId", takeover.takeoverId(), "beforeStatus", takeover.status(), "afterStatus", "IN_PROGRESS"));
        String userMessage = takeoverStartedContent();
        Map<String, Object> notificationData = data(
                "takeoverId", takeover.takeoverId(),
                "takeoverStatus", "IN_PROGRESS",
                "workOrderStatus", "PROCESSING");
        publishUserServiceEvent(ticket, "HUMAN_AGENT", userMessage, request.operatorId(),
                "TAKEOVER_STARTED",
                "人工客服接入通知",
                notificationData);
        ActionResult result = currentResult(ticketId, "人工接管已开始");
        logActionResult("人工接管开始完成", result, request.operatorId());
        return result;
    }

    @Transactional
    public ActionResult sendTakeoverMessage(String ticketId, TakeoverMessageRequest request) {
        WorkOrderView ticket = requireTicket(ticketId);
        HumanTakeoverView takeover = findTakeover(ticketId)
                .orElseThrow(() -> rejected("当前工单没有人工接管记录"));
        if (!"IN_PROGRESS".equals(takeover.status())) {
            throw rejected("只有人工接管中才能发送坐席消息");
        }

        String content = request.content().trim();
        LOGGER.info(
                "坐席发送人工消息 ticketId={} traceId={} takeoverId={} operatorId={} contentLength={}",
                ticketId,
                ticket.traceId(),
                takeover.takeoverId(),
                request.operatorId(),
                content.length());

        Map<String, Object> messageData = data(
                "takeoverId", takeover.takeoverId(),
                "takeoverStatus", takeover.status(),
                "workOrderStatus", ticket.status(),
                "payload", request.payload());
        insertUserVisibleMessage(ticket, "HUMAN_AGENT", content, request.operatorId(),
                "TAKEOVER_MESSAGE_SENT",
                messageData);
        insertWorkOrderAction(ticketId, ticket.traceId(), request.operatorId(), "TAKEOVER", content,
                data("subAction", "MESSAGE_SENT", "takeoverId", takeover.takeoverId(), "payload", request.payload()));
        insertAudit(ticket, request.operatorId(), "TAKEOVER_MESSAGE_SENT",
                data("takeoverId", takeover.takeoverId(), "contentLength", content.length(), "payload", request.payload()));
        publishNotificationEvent(ticket, "TAKEOVER_MESSAGE_SENT", "人工客服消息", content,
                request.operatorId(), messageData);

        ActionResult result = currentResult(ticketId, "人工消息已发送");
        logActionResult("坐席发送人工消息完成", result, request.operatorId());
        return result;
    }

    @Transactional
    public ActionResult finishTakeover(String ticketId, TakeoverFinishRequest request) {
        WorkOrderView ticket = requireTicket(ticketId);
        HumanTakeoverView takeover = findTakeover(ticketId)
                .orElseThrow(() -> rejected("当前工单没有人工接管记录"));
        rejectTerminalTakeover(takeover.status());

        String takeoverTarget = normalizeTakeoverTarget(request.resolutionStatus());
        String workOrderTarget = "RESOLVED".equals(takeoverTarget) ? "RESOLVED" : "CLOSED";
        LOGGER.info(
                "人工接管结束开始 ticketId={} traceId={} takeoverId={} operatorId={} targetTakeoverStatus={} targetWorkOrderStatus={}",
                ticketId,
                ticket.traceId(),
                takeover.takeoverId(),
                request.operatorId(),
                takeoverTarget,
                workOrderTarget);
        Map<String, Object> result = data(
                "takeoverStatus", takeoverTarget,
                "workOrderStatus", workOrderTarget,
                "comment", request.comment(),
                "operatorId", request.operatorId(),
                "result", request.result());

        jdbcTemplate.update(
                """
                UPDATE human_takeover
                SET status = ?,
                    assigned_agent = ?,
                    ended_at = CURRENT_TIMESTAMP(3)
                WHERE takeover_id = ?
                """,
                takeoverTarget,
                request.operatorId(),
                takeover.takeoverId());
        jdbcTemplate.update(
                """
                UPDATE work_order
                SET status = ?,
                    assigned_agent = ?,
                    resolution = CAST(? AS JSON),
                    resolved_at = CURRENT_TIMESTAMP(3)
                WHERE ticket_id = ?
                """,
                workOrderTarget,
                request.operatorId(),
                json(result),
                ticketId);
        updateSessionState(ticket.sessionId(), "CLOSED", "CLOSED");
        insertWorkOrderAction(ticketId, ticket.traceId(), request.operatorId(), "CLOSE", request.comment(),
                data("takeoverId", takeover.takeoverId(), "beforeStatus", takeover.status(), "afterStatus", takeoverTarget));
        insertAudit(ticket, request.operatorId(), "TAKEOVER_FINISHED",
                data("takeoverId", takeover.takeoverId(), "beforeStatus", takeover.status(), "afterStatus", takeoverTarget));
        String userMessage = takeoverFinishedContent(takeoverTarget);
        Map<String, Object> notificationData = data(
                "takeoverId", takeover.takeoverId(),
                "takeoverStatus", takeoverTarget,
                "workOrderStatus", workOrderTarget);
        publishUserServiceEvent(ticket, "HUMAN_AGENT", userMessage, request.operatorId(),
                "TAKEOVER_FINISHED",
                "人工客服处理结束通知",
                notificationData);
        ActionResult actionResult = currentResult(ticketId, "人工接管已结束");
        logActionResult("人工接管结束完成", actionResult, request.operatorId());
        return actionResult;
    }

    private SqlFilter buildTicketFilter(
            String status,
            String routeDecision,
            String riskLevel,
            String intent,
            String priority,
            String assignedAgent,
            String keyword,
            String createdAtFrom,
            String createdAtTo) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (hasText(status)) {
            where.append(" AND w.status = ?");
            args.add(status.trim());
        }
        if (hasText(routeDecision)) {
            where.append(" AND w.route_decision = ?");
            args.add(routeDecision.trim());
        }
        if (hasText(riskLevel)) {
            where.append(" AND w.risk_level = ?");
            args.add(riskLevel.trim());
        }
        if (hasText(intent)) {
            where.append(" AND w.intent = ?");
            args.add(intent.trim());
        }
        if (hasText(priority)) {
            where.append(" AND w.priority = ?");
            args.add(priority.trim());
        }
        if (hasText(assignedAgent)) {
            where.append(" AND w.assigned_agent = ?");
            args.add(assignedAgent.trim());
        }
        if (hasText(createdAtFrom)) {
            where.append(" AND w.created_at >= ?");
            args.add(createdAtFrom.trim());
        }
        if (hasText(createdAtTo)) {
            where.append(" AND w.created_at <= ?");
            args.add(createdAtTo.trim());
        }
        if (hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            where.append("""
                    AND (
                        w.ticket_id LIKE ?
                        OR w.session_id LIKE ?
                        OR w.user_id LIKE ?
                        OR w.intent LIKE ?
                    )
                    """);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return new SqlFilter(where.toString(), args);
    }

    private WorkOrderView requireTicket(String ticketId) {
        return findTicket(ticketId)
                .orElseThrow(() -> new WorkbenchOperationException(ErrorCode.TICKET_NOT_FOUND, "工单不存在"));
    }

    private ApprovalTaskView requireApproval(String ticketId) {
        return findApproval(ticketId)
                .orElseThrow(() -> rejected("当前工单没有审批任务"));
    }

    private Optional<WorkOrderView> findTicket(String ticketId) {
        return queryOptional(
                """
                SELECT
                    ticket_id, trace_id, session_id, user_id, intent, risk_level, route_decision,
                    status, priority, assigned_agent, reason, context_snapshot, resolution,
                    sla_deadline, created_at, updated_at, resolved_at
                FROM work_order
                WHERE ticket_id = ?
                """,
                this::mapWorkOrder,
                ticketId);
    }

    private Optional<ApprovalTaskView> findApproval(String ticketId) {
        return queryOptional(
                """
                SELECT
                    approval_id, ticket_id, trace_id, session_id, user_id, intent, approval_type,
                    risk_level, route_decision, status, priority, assigned_reviewer, risk_reason,
                    request_payload, context_snapshot, approval_result, expire_at, created_at,
                    updated_at, completed_at
                FROM approval_task
                WHERE ticket_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """,
                this::mapApproval,
                ticketId);
    }

    private Optional<HumanTakeoverView> findTakeover(String ticketId) {
        return queryOptional(
                """
                SELECT
                    takeover_id, ticket_id, trace_id, session_id, user_id, trigger_source, status,
                    priority, assigned_agent, reason, context_snapshot, started_at, ended_at,
                    created_at, updated_at
                FROM human_takeover
                WHERE ticket_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """,
                this::mapTakeover,
                ticketId);
    }

    private List<MessageView> listMessages(String sessionId) {
        return jdbcTemplate.query(
                """
                SELECT
                    message_id, trace_id, session_id, user_id, role, message_type, content,
                    quick_actions, intent, risk_level, route_decision, metadata, created_at
                FROM cs_message
                WHERE session_id = ?
                ORDER BY created_at ASC
                LIMIT 100
                """,
                this::mapMessage,
                sessionId);
    }

    private void claimApprovalIfOpen(ApprovalTaskView approval, OperatorActionRequest request) {
        if (!OPEN_APPROVAL_STATUSES.contains(approval.status())) {
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE approval_task
                SET status = 'CLAIMED',
                    assigned_reviewer = ?
                WHERE approval_id = ?
                """,
                request.operatorId(),
                approval.approvalId());
        insertApprovalAction(approval, request.operatorId(), "CLAIM", approval.status(), "CLAIMED",
                request.comment(), data("payload", request.payload()));
    }

    private void assignTakeoverIfOpen(HumanTakeoverView takeover, OperatorActionRequest request) {
        if (TERMINAL_TAKEOVER_STATUSES.contains(takeover.status())) {
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE human_takeover
                SET status = 'ASSIGNED',
                    assigned_agent = ?
                WHERE takeover_id = ? AND status IN ('REQUESTED', 'QUEUED', 'ASSIGNED')
                """,
                request.operatorId(),
                takeover.takeoverId());
    }

    private HumanTakeoverView createTakeover(WorkOrderView ticket, OperatorActionRequest request) {
        // 坐席也可以从普通工单主动发起接管，这里补一条 human_takeover 记录。
        String takeoverId = "ht_" + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO human_takeover (
                    takeover_id, ticket_id, trace_id, session_id, user_id, trigger_source,
                    status, priority, assigned_agent, reason, context_snapshot
                ) VALUES (?, ?, ?, ?, ?, 'HUMAN_ASSIGNMENT', 'REQUESTED', ?, ?, ?, CAST(? AS JSON))
                """,
                takeoverId,
                ticket.ticketId(),
                ticket.traceId(),
                ticket.sessionId(),
                ticket.userId(),
                ticket.priority(),
                request.operatorId(),
                textOr(request.comment(), "坐席主动接管"),
                json(data("source", "workbench", "payload", request.payload())));
        LOGGER.info(
                "坐席主动创建人工接管记录 ticketId={} traceId={} takeoverId={} operatorId={}",
                ticket.ticketId(),
                ticket.traceId(),
                takeoverId,
                request.operatorId());
        return findTakeover(ticket.ticketId())
                .orElseThrow(() -> rejected("人工接管记录创建失败"));
    }

    private void insertWorkOrderAction(
            String ticketId,
            String traceId,
            String operatorId,
            String actionType,
            String comment,
            Map<String, Object> actionData) {
        jdbcTemplate.update(
                """
                INSERT INTO work_order_action (
                    action_id, ticket_id, trace_id, operator_id, action_type, comment, action_data
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                """,
                "wa_" + UUID.randomUUID(),
                ticketId,
                traceId,
                operatorId,
                actionType,
                comment,
                json(actionData));
    }

    private void insertApprovalAction(
            ApprovalTaskView approval,
            String operatorId,
            String actionType,
            String beforeStatus,
            String afterStatus,
            String comment,
            Map<String, Object> actionData) {
        jdbcTemplate.update(
                """
                INSERT INTO approval_action (
                    action_id, approval_id, ticket_id, trace_id, operator_id, action_type,
                    before_status, after_status, comment, action_data
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                """,
                "aa_" + UUID.randomUUID(),
                approval.approvalId(),
                approval.ticketId(),
                approval.traceId(),
                operatorId,
                actionType,
                beforeStatus,
                afterStatus,
                comment,
                json(actionData));
    }

    private void insertUserVisibleMessage(
            WorkOrderView ticket,
            String role,
            String content,
            String operatorId,
            String actionType,
            Map<String, Object> eventData) {
        // 坐席处理结果需要回写到会话消息，用户端 H5 才能在原会话里看到人工处理进度。
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "workbench");
        metadata.put("ticketId", ticket.ticketId());
        metadata.put("operatorId", operatorId);
        metadata.put("actionType", actionType);
        if (eventData != null) {
            metadata.putAll(eventData);
        }

        String messageId = "m_" + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cs_message (
                    message_id, trace_id, session_id, user_id, role, message_type,
                    content, intent, risk_level, route_decision, metadata
                ) VALUES (?, ?, ?, ?, ?, 'TEXT', ?, ?, ?, ?, CAST(? AS JSON))
                """,
                messageId,
                ticket.traceId(),
                ticket.sessionId(),
                ticket.userId(),
                role,
                content,
                ticket.intent(),
                ticket.riskLevel(),
                ticket.routeDecision(),
                json(metadata));
        LOGGER.info(
                "写入用户可见坐席消息 ticketId={} traceId={} sessionId={} messageId={} role={} actionType={}",
                ticket.ticketId(),
                ticket.traceId(),
                ticket.sessionId(),
                messageId,
                role,
                actionType);
    }

    /**
     * 用户侧服务事件边界：先写用户可见消息，再投递通知事件。
     *
     * <p>Notification 是辅助链路，失败会在客户端记录 warn，不回滚坐席操作。
     */
    private void publishUserServiceEvent(
            WorkOrderView ticket,
            String role,
            String content,
            String operatorId,
            String eventType,
            String title,
            Map<String, Object> metadata) {
        insertUserVisibleMessage(ticket, role, content, operatorId, eventType, metadata);
        publishNotificationEvent(ticket, eventType, title, content, operatorId, metadata);
    }

    private void publishNotificationEvent(
            WorkOrderView ticket,
            String eventType,
            String title,
            String content,
            String operatorId,
            Map<String, Object> eventData) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intent", ticket.intent());
        payload.put("riskLevel", ticket.riskLevel());
        payload.put("routeDecision", ticket.routeDecision());
        if (eventData != null) {
            payload.putAll(eventData);
        }
        notificationEventClient.publish(new NotificationEventRequest(
                "ntf_" + UUID.randomUUID(),
                ticket.traceId(),
                "smartcs-workbench",
                eventType,
                ticket.userId(),
                ticket.sessionId(),
                ticket.ticketId(),
                operatorId,
                "USER_SESSION",
                title,
                content,
                payload,
                Instant.now()));
    }

    private Map<String, Object> approvalConclusion(ApprovalDecisionRequest request, String expectedDecisionType) {
        String decisionType = normalizeApprovalDecisionType(request.decisionType(), expectedDecisionType);
        if (!expectedDecisionType.equals(decisionType)) {
            throw rejected("审批结论类型与当前操作不匹配");
        }
        return data(
                "decision", decisionType,
                "decisionType", decisionType,
                "comment", request.comment(),
                "operatorId", request.operatorId(),
                "result", request.result());
    }

    private String normalizeApprovalDecisionType(String decisionType, String fallback) {
        String normalized = textOr(decisionType, fallback).trim().toUpperCase().replace('-', '_');
        if (!APPROVAL_DECISION_TYPES.contains(normalized)) {
            throw rejected("审批结论类型只支持 APPROVED、REJECTED、REQUEST_MATERIALS、TRANSFER_TAKEOVER");
        }
        return normalized;
    }

    private String approvalApprovedContent(ApprovalTaskView approval) {
        if ("REFUND".equalsIgnoreCase(approval.approvalType())) {
            return "你的退款申请已通过人工审核，后续处理会按平台流程继续推进。";
        }
        if ("EXCHANGE".equalsIgnoreCase(approval.approvalType())) {
            return "你的换货申请已通过人工审核，后续处理会按平台流程继续推进。";
        }
        return "你的申请已通过人工审核，后续处理会按平台流程继续推进。";
    }

    private String approvalRejectedContent(ApprovalTaskView approval) {
        if ("REFUND".equalsIgnoreCase(approval.approvalType())) {
            return "你的退款申请未通过人工审核，如有疑问可以继续联系人工客服。";
        }
        if ("EXCHANGE".equalsIgnoreCase(approval.approvalType())) {
            return "你的换货申请未通过人工审核，如有疑问可以继续联系人工客服。";
        }
        return "你的申请未通过人工审核，如有疑问可以继续联系人工客服。";
    }

    private String approvalMaterialsRequestedContent(ApprovalDecisionRequest request) {
        return "你的申请还需要补充材料：" + textOr(request.comment(), "请补充相关凭证或说明后继续处理。");
    }

    private String approvalTransferredToTakeoverContent() {
        return "你的申请已转人工客服继续处理，坐席稍后会接入。";
    }

    private String takeoverStartedContent() {
        return "人工客服已接入，正在为你处理，请稍等。";
    }

    private String takeoverFinishedContent(String takeoverStatus) {
        if ("CANCELLED".equalsIgnoreCase(takeoverStatus)) {
            return "人工客服已结束本次处理，如仍需帮助可以继续发起咨询。";
        }
        return "人工客服已处理完成，本次服务已结束。";
    }

    private void insertAudit(WorkOrderView ticket, String operatorId, String eventType, Map<String, Object> eventData) {
        // 审计日志用于问题追踪和后续合规检查，所有坐席动作都保留一条独立事件。
        jdbcTemplate.update(
                """
                INSERT INTO audit_log (
                    event_id, trace_id, session_id, ticket_id, user_id, operator_id,
                    event_type, event_data, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?)
                """,
                "evt_" + UUID.randomUUID(),
                ticket.traceId(),
                ticket.sessionId(),
                ticket.ticketId(),
                ticket.userId(),
                operatorId,
                eventType,
                json(eventData),
                Timestamp.from(Instant.now()));
        LOGGER.debug(
                "写入坐席审计日志 ticketId={} traceId={} operatorId={} eventType={}",
                ticket.ticketId(),
                ticket.traceId(),
                operatorId,
                eventType);
    }

    private void updateSessionState(String sessionId, String status, String dialogState) {
        jdbcTemplate.update(
                """
                UPDATE cs_session
                SET status = ?,
                    dialog_state = ?
                WHERE session_id = ?
                """,
                status,
                dialogState,
                sessionId);
    }

    private ActionResult currentResult(String ticketId, String message) {
        WorkOrderView ticket = requireTicket(ticketId);
        ApprovalTaskView approval = findApproval(ticketId).orElse(null);
        HumanTakeoverView takeover = findTakeover(ticketId).orElse(null);
        return new ActionResult(
                ticketId,
                ticket.status(),
                approval == null ? null : approval.status(),
                takeover == null ? null : takeover.status(),
                message);
    }

    private void logActionResult(String actionName, ActionResult result, String operatorId) {
        LOGGER.info(
                "{} ticketId={} operatorId={} workOrderStatus={} approvalStatus={} takeoverStatus={}",
                actionName,
                result.ticketId(),
                operatorId,
                result.workOrderStatus(),
                result.approvalStatus(),
                result.takeoverStatus());
    }

    private void rejectTerminalWorkOrder(String status) {
        if (TERMINAL_WORK_ORDER_STATUSES.contains(status)) {
            throw rejected("当前工单已处于终态，不能继续操作");
        }
    }

    private void requireOpenApproval(String status) {
        if (!OPEN_APPROVAL_STATUSES.contains(status)) {
            throw rejected("当前审批任务状态不允许审核");
        }
    }

    private void rejectTerminalTakeover(String status) {
        if (TERMINAL_TAKEOVER_STATUSES.contains(status)) {
            throw rejected("当前人工接管已结束，不能继续操作");
        }
    }

    private String normalizeTakeoverTarget(String resolutionStatus) {
        String target = textOr(resolutionStatus, "RESOLVED").toUpperCase();
        if (!TERMINAL_TAKEOVER_STATUSES.contains(target)) {
            throw rejected("人工接管结束状态只能是 RESOLVED 或 CANCELLED");
        }
        return target;
    }

    private WorkbenchOperationException rejected(String message) {
        return new WorkbenchOperationException(ErrorCode.TICKET_ACTION_REJECTED, message);
    }

    private TicketSummary mapTicketSummary(ResultSet rs, int rowNum) throws SQLException {
        return new TicketSummary(
                rs.getString("ticket_id"),
                rs.getString("trace_id"),
                rs.getString("session_id"),
                rs.getString("user_id"),
                rs.getString("intent"),
                rs.getString("risk_level"),
                rs.getString("route_decision"),
                rs.getString("status"),
                rs.getString("priority"),
                rs.getString("assigned_agent"),
                rs.getString("reason"),
                instant(rs, "sla_deadline"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                rs.getString("approval_id"),
                rs.getString("approval_type"),
                rs.getString("approval_status"),
                rs.getString("takeover_id"),
                rs.getString("takeover_status"));
    }

    private WorkOrderView mapWorkOrder(ResultSet rs, int rowNum) throws SQLException {
        return new WorkOrderView(
                rs.getString("ticket_id"),
                rs.getString("trace_id"),
                rs.getString("session_id"),
                rs.getString("user_id"),
                rs.getString("intent"),
                rs.getString("risk_level"),
                rs.getString("route_decision"),
                rs.getString("status"),
                rs.getString("priority"),
                rs.getString("assigned_agent"),
                rs.getString("reason"),
                mapJson(rs, "context_snapshot"),
                mapJson(rs, "resolution"),
                instant(rs, "sla_deadline"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "resolved_at"));
    }

    private ApprovalTaskView mapApproval(ResultSet rs, int rowNum) throws SQLException {
        return new ApprovalTaskView(
                rs.getString("approval_id"),
                rs.getString("ticket_id"),
                rs.getString("trace_id"),
                rs.getString("session_id"),
                rs.getString("user_id"),
                rs.getString("intent"),
                rs.getString("approval_type"),
                rs.getString("risk_level"),
                rs.getString("route_decision"),
                rs.getString("status"),
                rs.getString("priority"),
                rs.getString("assigned_reviewer"),
                rs.getString("risk_reason"),
                mapJson(rs, "request_payload"),
                mapJson(rs, "context_snapshot"),
                mapJson(rs, "approval_result"),
                instant(rs, "expire_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "completed_at"));
    }

    private HumanTakeoverView mapTakeover(ResultSet rs, int rowNum) throws SQLException {
        return new HumanTakeoverView(
                rs.getString("takeover_id"),
                rs.getString("ticket_id"),
                rs.getString("trace_id"),
                rs.getString("session_id"),
                rs.getString("user_id"),
                rs.getString("trigger_source"),
                rs.getString("status"),
                rs.getString("priority"),
                rs.getString("assigned_agent"),
                rs.getString("reason"),
                mapJson(rs, "context_snapshot"),
                instant(rs, "started_at"),
                instant(rs, "ended_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private MessageView mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return new MessageView(
                rs.getString("message_id"),
                rs.getString("trace_id"),
                rs.getString("session_id"),
                rs.getString("user_id"),
                rs.getString("role"),
                rs.getString("message_type"),
                rs.getString("content"),
                listJson(rs, "quick_actions"),
                rs.getString("intent"),
                rs.getString("risk_level"),
                rs.getString("route_decision"),
                mapJson(rs, "metadata"),
                instant(rs, "created_at"));
    }

    private ActionLogView mapActionLog(ResultSet rs, int rowNum) throws SQLException {
        return new ActionLogView(
                rs.getString("action_id"),
                rs.getString("source"),
                rs.getString("ticket_id"),
                rs.getString("trace_id"),
                rs.getString("operator_id"),
                rs.getString("action_type"),
                rs.getString("before_status"),
                rs.getString("after_status"),
                rs.getString("comment"),
                mapJson(rs, "action_data"),
                instant(rs, "created_at"));
    }

    private <T> Optional<T> queryOptional(String sql, RowMapper<T> mapper, Object... args) {
        List<T> rows = jdbcTemplate.query(sql, mapper, args);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Map<String, Object> mapJson(ResultSet rs, String column) throws SQLException {
        String value = jsonColumn(rs, column);
        if (!hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of("raw", value);
        }
    }

    private List<Object> listJson(ResultSet rs, String column) throws SQLException {
        String value = jsonColumn(rs, column);
        if (!hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, LIST_TYPE);
        } catch (JsonProcessingException e) {
            return List.of(value);
        }
    }

    private String jsonColumn(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : value.toString();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize JSON payload", e);
        }
    }

    private Map<String, Object> data(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            if (value != null) {
                map.put((String) pairs[i], value);
            }
        }
        return map;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String textOr(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private record SqlFilter(String whereSql, List<Object> args) {
    }
}
