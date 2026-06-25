package com.smartcs.agent.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.common.domain.AgentReply;
import com.smartcs.agent.common.domain.ChatActionRequest;
import com.smartcs.agent.common.domain.ChatRequest;
import com.smartcs.agent.common.domain.QuickAction;
import com.smartcs.agent.common.enums.MessageType;
import com.smartcs.agent.common.enums.RiskLevel;
import com.smartcs.agent.common.enums.RouteDecision;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.core.knowledge.KnowledgeFaqClient;
import com.smartcs.agent.core.knowledge.KnowledgeFaqDtos.FaqQueryRequest;
import com.smartcs.agent.core.knowledge.KnowledgeFaqDtos.FaqQueryResult;
import com.smartcs.agent.core.skill.SkillEngineClient;
import com.smartcs.agent.core.skill.SkillExecutionDtos.SkillExecutionRequest;
import com.smartcs.agent.core.skill.SkillExecutionDtos.SkillExecutionResult;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Agent 编排服务：先用关键词 mock NLU 打通最小链路，后续可替换为真实模型和业务编排。
 */
@Service
public class MockAgentOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockAgentOrchestrator.class);

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)\\s*(元|块|rmb|RMB)?");
    private static final Pattern ORDER_NO_PATTERN =
            Pattern.compile("(?i)(?:订单号|订单|order)\\s*[#：:=-]?\\s*([A-Z0-9][A-Z0-9-]{5,})");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SkillEngineClient skillEngineClient;
    private final KnowledgeFaqClient knowledgeFaqClient;

    public MockAgentOrchestrator(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            SkillEngineClient skillEngineClient,
            KnowledgeFaqClient knowledgeFaqClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.skillEngineClient = skillEngineClient;
        this.knowledgeFaqClient = knowledgeFaqClient;
    }

    public AgentReply process(ChatRequest request) {
        String traceId = textOr(request.traceId(), TraceIds.newTraceId());
        String sessionId = textOr(request.sessionId(), "s_" + UUID.randomUUID());
        String userId = request.userId();
        String channel = textOr(request.channel(), "h5");
        String content = request.content();
        String userMessageId = "m_" + UUID.randomUUID();

        LOGGER.info(
                "Agent编排开始 traceId={} sessionId={} userId={} channel={} contentLength={}",
                traceId,
                sessionId,
                userId,
                channel,
                contentLength(content));

        // 1. 最小版 NLU：当前只按关键词识别意图，后续这里替换成真实模型调用。
        IntentGuess intentGuess = detectIntent(content);
        SkillConfig skillConfig = findSkill(intentGuess.intent()).orElse(null);
        RuleDecision ruleDecision = decideRisk(intentGuess, skillConfig, content);
        LOGGER.info(
                "Agent路由决策 traceId={} sessionId={} intent={} confidence={} skillId={} riskLevel={} routeDecision={} reasonCode={}",
                traceId,
                sessionId,
                intentGuess.intent(),
                intentGuess.confidence(),
                skillConfig == null ? "" : skillConfig.skillId(),
                ruleDecision.riskLevel(),
                ruleDecision.routeDecision(),
                ruleDecision.reasonCode());

        // 2. 落库会话、消息、意图和风控结果，保证坐席端可以追踪完整上下文。
        upsertSession(traceId, sessionId, userId, channel, intentGuess.intent(), ruleDecision);
        insertUserMessage(userMessageId, traceId, sessionId, userId, channel, content);
        insertIntentRecord(traceId, sessionId, userMessageId, intentGuess);
        insertRiskDecision(traceId, sessionId, userMessageId, intentGuess.intent(), ruleDecision);

        // 3. 敏感操作不直接执行，统一创建人工工单、审批任务或接管记录。
        String ticketId = null;
        if (requiresHuman(ruleDecision.routeDecision())) {
            ticketId = createWorkOrder(traceId, sessionId, userId, intentGuess.intent(), ruleDecision);
            createWorkOrderAction(ticketId, traceId, "system", "CREATE", ruleDecision.reason());
            if (ruleDecision.routeDecision() == RouteDecision.HUMAN_REVIEW) {
                createApprovalTask(ticketId, traceId, sessionId, userId, intentGuess.intent(), ruleDecision, content);
            }
            if (ruleDecision.routeDecision() == RouteDecision.HUMAN_TAKEOVER) {
                createHumanTakeover(ticketId, traceId, sessionId, userId, ruleDecision, content);
            }
        }

        // 4. 自动路径交给 Skill Engine；失败时保留本地执行日志兜底，避免聊天链路中断。
        SkillExecutionResult skillExecution = null;
        FaqQueryResult faqResult = null;
        if (canCallKnowledge(intentGuess.intent(), ruleDecision.routeDecision())) {
            faqResult = knowledgeFaqClient.query(new FaqQueryRequest(
                            traceId,
                            sessionId,
                            userId,
                            channel,
                            content))
                    .orElse(null);
            if (faqResult != null) {
                LOGGER.info(
                        "Agent收到Knowledge FAQ结果 traceId={} sessionId={} answerId={} matched={} confidence={}",
                        traceId,
                        sessionId,
                        faqResult.answerId(),
                        faqResult.matched(),
                        faqResult.confidence());
            }
        }
        if (skillConfig != null && canCallSkillEngine(ruleDecision.routeDecision())) {
            skillExecution = skillEngineClient.execute(buildSkillExecutionRequest(
                            traceId,
                            sessionId,
                            userMessageId,
                            userId,
                            channel,
                            content,
                            intentGuess,
                            skillConfig,
                            ruleDecision,
                            request.metadata()))
                    .orElse(null);
            if (skillExecution != null) {
                LOGGER.info(
                        "Agent收到Skill Engine执行结果 traceId={} sessionId={} executionId={} skillId={} status={}",
                        traceId,
                        sessionId,
                        skillExecution.executionId(),
                        skillExecution.skillId(),
                        skillExecution.status());
            }
        }

        if (skillConfig != null && skillExecution == null) {
            LOGGER.info(
                    "Agent使用本地技能日志兜底 traceId={} sessionId={} messageId={} skillId={} routeDecision={}",
                    traceId,
                    sessionId,
                    userMessageId,
                    skillConfig.skillId(),
                    ruleDecision.routeDecision());
            insertSkillExecutionLog(traceId, sessionId, userMessageId, userId, intentGuess.intent(), skillConfig, ruleDecision);
        }

        AgentReply reply = buildReply(
                traceId,
                sessionId,
                intentGuess,
                ruleDecision,
                ticketId,
                skillConfig,
                skillExecution,
                faqResult);
        insertAgentMessage(reply, userId);
        LOGGER.info(
                "Agent编排完成 traceId={} sessionId={} replyId={} routeDecision={} ticketId={} skillExecutionId={}",
                traceId,
                sessionId,
                reply.replyId(),
                reply.routeDecision(),
                ticketId,
                skillExecution == null ? "" : skillExecution.executionId());
        return reply;
    }

    public AgentReply processAction(ChatActionRequest request) {
        String requestTraceId = textOr(request.traceId(), TraceIds.newTraceId());
        String actionType = normalizeActionType(request.actionType());
        Optional<SessionSnapshot> optionalSession = findSession(request.sessionId());
        if (optionalSession.isEmpty()) {
            LOGGER.warn(
                    "聊天动作未找到会话 traceId={} sessionId={} userId={} actionType={}",
                    requestTraceId,
                    request.sessionId(),
                    request.userId(),
                    actionType);
            return buildTransientActionReply(
                    requestTraceId,
                    request.sessionId(),
                    "当前会话不存在，无法继续该操作。",
                    actionType);
        }

        SessionSnapshot session = optionalSession.get();
        String traceId = textOr(session.traceId(), requestTraceId);
        String userId = textOr(session.userId(), request.userId());
        String actionMessageId = "m_" + UUID.randomUUID();

        LOGGER.info(
                "Agent处理聊天动作 traceId={} sessionId={} userId={} actionType={} currentIntent={} dialogState={}",
                traceId,
                session.sessionId(),
                userId,
                actionType,
                session.currentIntent(),
                session.dialogState());

        insertActionMessage(actionMessageId, traceId, session, request, actionType);

        AgentReply reply = switch (actionType) {
            case "CONFIRM" -> confirmPendingAction(traceId, actionMessageId, userId, session, request);
            case "CANCEL" -> cancelPendingAction(traceId, actionMessageId, userId, session, request);
            default -> buildActionReply(
                    traceId,
                    session.sessionId(),
                    actionType,
                    session.currentIntent(),
                    null,
                    null,
                    RiskLevel.L0,
                    RouteDecision.REJECT,
                    "当前操作暂不支持。",
                    List.of(),
                    Map.of("unsupportedActionType", actionType));
        };

        insertAgentMessage(reply, userId);
        LOGGER.info(
                "Agent聊天动作完成 traceId={} sessionId={} actionType={} replyId={} routeDecision={} skillExecutionId={}",
                traceId,
                session.sessionId(),
                actionType,
                reply.replyId(),
                reply.routeDecision(),
                reply.metadata().getOrDefault("skillExecutionId", ""));
        return reply;
    }

    private AgentReply confirmPendingAction(
            String traceId,
            String actionMessageId,
            String userId,
            SessionSnapshot session,
            ChatActionRequest request) {
        if (!"WAITING_CONFIRM".equals(session.dialogState())) {
            return buildActionReply(
                    traceId,
                    session.sessionId(),
                    "CONFIRM",
                    session.currentIntent(),
                    null,
                    null,
                    RiskLevel.L0,
                    RouteDecision.REJECT,
                    "当前没有等待确认的操作。",
                    List.of(),
                    Map.of("dialogState", textOr(session.dialogState(), "")));
        }

        SkillConfig skillConfig = findSkill(session.currentIntent()).orElse(null);
        if (skillConfig == null) {
            RuleDecision takeoverDecision = new RuleDecision(
                    RiskLevel.L3,
                    RouteDecision.HUMAN_TAKEOVER,
                    "CONFIRM_CONTEXT_INCOMPLETE",
                    "确认操作上下文不完整，转人工处理");
            String ticketId = createWorkOrder(
                    traceId,
                    session.sessionId(),
                    userId,
                    textOr(session.currentIntent(), "unknown"),
                    takeoverDecision);
            createWorkOrderAction(ticketId, traceId, "system", "CREATE", takeoverDecision.reason());
            createHumanTakeover(ticketId, traceId, session.sessionId(), userId, takeoverDecision, actionContent(request, "确认继续"));
            updateSessionState(session.sessionId(), "HUMAN_TAKEOVER", "HUMAN_TAKEOVER");
            return buildActionReply(
                    traceId,
                    session.sessionId(),
                    "CONFIRM",
                    session.currentIntent(),
                    null,
                    ticketId,
                    takeoverDecision.riskLevel(),
                    takeoverDecision.routeDecision(),
                    "当前操作上下文不完整，我已为你转人工处理" + suffixTicket(ticketId) + "。",
                    List.of(),
                    Map.of("reasonCode", takeoverDecision.reasonCode()));
        }

        RuleDecision confirmedDecision = new RuleDecision(
                RiskLevel.L2,
                RouteDecision.AUTO_EXECUTE,
                "USER_CONFIRMED",
                "用户确认后继续执行");
        SkillExecutionResult skillExecution = skillEngineClient.execute(buildConfirmedSkillExecutionRequest(
                        traceId,
                        actionMessageId,
                        userId,
                        session,
                        request,
                        skillConfig,
                        confirmedDecision))
                .orElse(null);
        if (skillExecution == null) {
            insertSkillExecutionLog(
                    traceId,
                    session.sessionId(),
                    actionMessageId,
                    userId,
                    session.currentIntent(),
                    skillConfig,
                    confirmedDecision);
        }

        updateSessionState(session.sessionId(), "ACTIVE", "COMPLETED");
        String content = textOr(
                skillExecutionMessage(skillExecution),
                "已确认继续，当前已记录执行请求，真实业务 API 后续接入。");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reasonCode", confirmedDecision.reasonCode());
        if (skillExecution != null) {
            metadata.put("skillExecutionId", skillExecution.executionId());
            metadata.put("skillExecutionStatus", skillExecution.status());
        }
        return buildActionReply(
                traceId,
                session.sessionId(),
                "CONFIRM",
                session.currentIntent(),
                skillConfig,
                null,
                confirmedDecision.riskLevel(),
                confirmedDecision.routeDecision(),
                content,
                List.of(),
                metadata);
    }

    private AgentReply cancelPendingAction(
            String traceId,
            String actionMessageId,
            String userId,
            SessionSnapshot session,
            ChatActionRequest request) {
        SkillConfig skillConfig = findSkill(session.currentIntent()).orElse(null);
        RuleDecision cancelDecision = new RuleDecision(
                RiskLevel.L0,
                RouteDecision.REJECT,
                "USER_CANCELLED",
                "用户取消确认后执行");
        if (skillConfig != null) {
            insertSkillExecutionLog(
                    traceId,
                    session.sessionId(),
                    actionMessageId,
                    userId,
                    session.currentIntent(),
                    skillConfig,
                    cancelDecision);
        }
        updateSessionState(session.sessionId(), "ACTIVE", "COMPLETED");
        return buildActionReply(
                traceId,
                session.sessionId(),
                "CANCEL",
                session.currentIntent(),
                skillConfig,
                null,
                cancelDecision.riskLevel(),
                cancelDecision.routeDecision(),
                "已取消本次操作。",
                List.of(),
                Map.of("reasonCode", cancelDecision.reasonCode()));
    }

    private SkillExecutionRequest buildConfirmedSkillExecutionRequest(
            String traceId,
            String messageId,
            String userId,
            SessionSnapshot session,
            ChatActionRequest request,
            SkillConfig skillConfig,
            RuleDecision decision) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("channel", textOr(request.channel(), session.channel()));
        context.put("actionType", request.actionType());
        context.put("actionId", request.actionId());
        context.put("confirmed", true);
        context.put("mock", true);
        return new SkillExecutionRequest(
                traceId,
                session.sessionId(),
                messageId,
                userId,
                session.currentIntent(),
                skillConfig.skillId(),
                decision.riskLevel(),
                decision.routeDecision(),
                request.payload() == null ? Map.of() : request.payload(),
                context);
    }

    private Optional<SessionSnapshot> findSession(String sessionId) {
        List<SessionSnapshot> sessions = jdbcTemplate.query(
                """
                SELECT session_id, trace_id, user_id, channel, status, dialog_state, current_intent
                FROM cs_session
                WHERE session_id = ?
                """,
                (rs, rowNum) -> new SessionSnapshot(
                        rs.getString("session_id"),
                        rs.getString("trace_id"),
                        rs.getString("user_id"),
                        rs.getString("channel"),
                        rs.getString("status"),
                        rs.getString("dialog_state"),
                        rs.getString("current_intent")),
                sessionId);
        return sessions.stream().findFirst();
    }

    private void insertActionMessage(
            String messageId,
            String traceId,
            SessionSnapshot session,
            ChatActionRequest request,
            String actionType) {
        jdbcTemplate.update(
                """
                INSERT INTO cs_message (
                    message_id, trace_id, session_id, user_id, role, message_type, content,
                    intent, metadata
                ) VALUES (?, ?, ?, ?, 'USER', 'ACTION', ?, ?, CAST(? AS JSON))
                """,
                messageId,
                traceId,
                session.sessionId(),
                textOr(session.userId(), request.userId()),
                actionContent(request, actionType),
                session.currentIntent(),
                json(actionMetadata(request, actionType)));
    }

    private Map<String, Object> actionMetadata(ChatActionRequest request, String actionType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actionType", actionType);
        metadata.put("actionId", textOr(request.actionId(), ""));
        metadata.put("payload", request.payload() == null ? Map.of() : request.payload());
        metadata.put("requestMetadata", request.metadata() == null ? Map.of() : request.metadata());
        return metadata;
    }

    private AgentReply buildActionReply(
            String traceId,
            String sessionId,
            String actionType,
            String intent,
            SkillConfig skillConfig,
            String ticketId,
            RiskLevel riskLevel,
            RouteDecision routeDecision,
            String content,
            List<QuickAction> quickActions,
            Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("intent", textOr(intent, ""));
        metadata.put("skillId", skillConfig == null ? "" : skillConfig.skillId());
        metadata.put("actionType", actionType);
        metadata.put("mock", true);
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }
        return new AgentReply(
                "r_" + UUID.randomUUID(),
                traceId,
                sessionId,
                MessageType.TEXT,
                content,
                quickActions,
                riskLevel,
                routeDecision,
                ticketId,
                metadata,
                Instant.now());
    }

    private AgentReply buildTransientActionReply(String traceId, String sessionId, String content, String actionType) {
        return buildActionReply(
                traceId,
                sessionId,
                actionType,
                "",
                null,
                null,
                RiskLevel.L0,
                RouteDecision.REJECT,
                content,
                List.of(),
                Map.of());
    }

    private void updateSessionState(String sessionId, String status, String dialogState) {
        jdbcTemplate.update(
                """
                UPDATE cs_session
                SET status = ?,
                    dialog_state = ?,
                    last_message_at = CURRENT_TIMESTAMP(3)
                WHERE session_id = ?
                """,
                status,
                dialogState,
                sessionId);
    }

    private String normalizeActionType(String actionType) {
        return actionType == null ? "" : actionType.trim().toUpperCase();
    }

    private String actionContent(ChatActionRequest request, String actionType) {
        if (request.content() != null && !request.content().isBlank()) {
            return request.content();
        }
        return switch (actionType) {
            case "CONFIRM" -> "确认继续";
            case "CANCEL" -> "取消";
            default -> actionType;
        };
    }

    private IntentGuess detectIntent(String content) {
        String lower = content == null ? "" : content.toLowerCase();
        // FAQ 只处理政策、规则、时效这类知识问法；敏感操作请求仍交给人工审核链路。
        if (isFaqQuestion(lower)) {
            return new IntentGuess("faq.query", 0.84);
        }
        if (containsAny(lower, "退款", "退货", "退钱", "refund")) {
            return new IntentGuess("refund.apply", 0.93);
        }
        if (containsAny(lower, "换货", "换一个", "exchange")) {
            return new IntentGuess("exchange.apply", 0.91);
        }
        if (containsAny(lower, "改地址", "修改地址", "换地址", "收货地址")) {
            return new IntentGuess("order.modify_address", 0.88);
        }
        if (containsAny(lower, "物流", "快递", "运单", "包裹", "配送", "到哪")) {
            return new IntentGuess("logistics.query", 0.87);
        }
        if (containsAny(lower, "订单", "查询")) {
            return new IntentGuess("order.query", 0.86);
        }
        return new IntentGuess("unknown", 0.40);
    }

    private Optional<SkillConfig> findSkill(String intent) {
        List<SkillConfig> skills = jdbcTemplate.query(
                """
                SELECT skill_id, intent, base_risk_level, execution_type
                FROM skill_registry
                WHERE intent = ? AND enabled = 1 AND status = 'ACTIVE'
                ORDER BY version DESC
                LIMIT 1
                """,
                (rs, rowNum) -> new SkillConfig(
                        rs.getString("skill_id"),
                        rs.getString("intent"),
                        RiskLevel.valueOf(rs.getString("base_risk_level")),
                        rs.getString("execution_type")),
                intent);
        return skills.stream().findFirst();
    }

    private RuleDecision decideRisk(IntentGuess intentGuess, SkillConfig skillConfig, String content) {
        List<RiskRule> rules = loadRules(intentGuess.intent());

        if (intentGuess.confidence() < 0.50) {
            return fromRule(findRule(rules, "R003_LOW_CONFIDENCE_TAKEOVER"), "意图识别置信度较低");
        }
        if (containsAny(content, "投诉", "生气", "差评", "人工", "客服")) {
            return fromRule(findRule(rules, "R004_NEGATIVE_EMOTION_TAKEOVER"), "用户表达强烈情绪或请求人工");
        }
        if ("refund.apply".equals(intentGuess.intent())) {
            return fromRule(findRule(rules, "R001_REFUND_REVIEW"), "退款申请需要人工审核");
        }
        if ("exchange.apply".equals(intentGuess.intent())) {
            return fromRule(findRule(rules, "R002_EXCHANGE_REVIEW"), "换货申请需要人工审核");
        }
        if (hasHighAmount(content)) {
            return fromRule(findRule(rules, "R005_HIGH_AMOUNT_REVIEW"), "涉及高金额操作");
        }
        if ("order.modify_address".equals(intentGuess.intent()) && containsAny(content, "签收", "已收货")) {
            return fromRule(findRule(rules, "R008_SIGNED_ADDRESS_REJECT"), "已签收订单修改地址需要人工审核");
        }
        if ("order.modify_address".equals(intentGuess.intent()) && containsAny(content, "发货", "出库", "物流")) {
            return fromRule(findRule(rules, "R007_SHIPPED_ADDRESS_CONFIRM"), "履约中订单修改地址需要用户确认");
        }
        if ("order.modify_address".equals(intentGuess.intent())) {
            return new RuleDecision(
                    RiskLevel.L2,
                    RouteDecision.CONFIRM_BEFORE_EXECUTE,
                    "ADDRESS_MODIFY_CONFIRM",
                    "修改收货地址需要用户确认后执行");
        }

        if ("faq.query".equals(intentGuess.intent())) {
            return new RuleDecision(RiskLevel.L0, RouteDecision.AUTO_REPLY, "FAQ_AUTO_REPLY", "FAQ 知识库自动回答");
        }
        if (skillConfig == null) {
            return new RuleDecision(RiskLevel.L3, RouteDecision.HUMAN_TAKEOVER, "NO_SKILL", "未找到可用技能");
        }
        return switch (skillConfig.baseRiskLevel()) {
            case L0 -> new RuleDecision(RiskLevel.L0, RouteDecision.AUTO_REPLY, "BASE_L0", "低风险自动回复");
            case L1 -> new RuleDecision(RiskLevel.L1, RouteDecision.AUTO_EXECUTE, "BASE_L1", "低风险技能可自动处理");
            case L2 -> new RuleDecision(RiskLevel.L2, RouteDecision.CONFIRM_BEFORE_EXECUTE, "BASE_L2", "执行前需要用户确认");
            case L3 -> new RuleDecision(RiskLevel.L3, RouteDecision.HUMAN_REVIEW, "BASE_L3", "高风险技能进入人工审核");
        };
    }

    private List<RiskRule> loadRules(String intent) {
        return jdbcTemplate.query(
                """
                SELECT rule_id, target_risk_level, route_decision, priority, description
                FROM risk_rule
                WHERE enabled = 1 AND (intent_pattern = ? OR intent_pattern IS NULL)
                ORDER BY priority DESC, rule_id
                """,
                (rs, rowNum) -> new RiskRule(
                        rs.getString("rule_id"),
                        RiskLevel.valueOf(rs.getString("target_risk_level")),
                        RouteDecision.valueOf(rs.getString("route_decision")),
                        rs.getInt("priority"),
                        rs.getString("description")),
                intent);
    }

    private RiskRule findRule(List<RiskRule> rules, String ruleId) {
        return rules.stream()
                .filter(rule -> rule.ruleId().equals(ruleId))
                .findFirst()
                .orElse(null);
    }

    private RuleDecision fromRule(RiskRule rule, String fallbackReason) {
        if (rule == null) {
            return new RuleDecision(RiskLevel.L3, RouteDecision.HUMAN_TAKEOVER, "RULE_FALLBACK", fallbackReason);
        }
        return new RuleDecision(rule.riskLevel(), rule.routeDecision(), rule.ruleId(), textOr(rule.description(), fallbackReason));
    }

    private void upsertSession(String traceId, String sessionId, String userId, String channel, String intent, RuleDecision decision) {
        String status = decision.routeDecision() == RouteDecision.HUMAN_TAKEOVER ? "HUMAN_TAKEOVER" : "ACTIVE";
        String dialogState = switch (decision.routeDecision()) {
            case HUMAN_REVIEW -> "HUMAN_REVIEW";
            case HUMAN_TAKEOVER -> "HUMAN_TAKEOVER";
            case CONFIRM_BEFORE_EXECUTE -> "WAITING_CONFIRM";
            case AUTO_EXECUTE -> "EXECUTING";
            default -> "COMPLETED";
        };
        jdbcTemplate.update(
                """
                INSERT INTO cs_session (
                    session_id, trace_id, user_id, channel, status, dialog_state, current_intent, slots, context_snapshot, last_message_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, JSON_OBJECT(), JSON_OBJECT('mock', true), CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE
                    trace_id = VALUES(trace_id),
                    status = VALUES(status),
                    dialog_state = VALUES(dialog_state),
                    current_intent = VALUES(current_intent),
                    context_snapshot = VALUES(context_snapshot),
                    last_message_at = VALUES(last_message_at)
                """,
                sessionId, traceId, userId, channel, status, dialogState, intent);
    }

    private void insertUserMessage(String messageId, String traceId, String sessionId, String userId, String channel, String content) {
        jdbcTemplate.update(
                """
                INSERT INTO cs_message (
                    message_id, trace_id, session_id, user_id, role, message_type, content, metadata
                ) VALUES (?, ?, ?, ?, 'USER', 'TEXT', ?, CAST(? AS JSON))
                """,
                messageId, traceId, sessionId, userId, content, json(Map.of("channel", channel)));
    }

    private void insertIntentRecord(String traceId, String sessionId, String messageId, IntentGuess intentGuess) {
        jdbcTemplate.update(
                """
                INSERT INTO intent_record (
                    trace_id, session_id, message_id, intent, confidence, entities, reasoning, model_name, model_version
                ) VALUES (?, ?, ?, ?, ?, JSON_ARRAY(), ?, 'mock-keyword-nlu', '1.0.0')
                """,
                traceId,
                sessionId,
                messageId,
                intentGuess.intent(),
                BigDecimal.valueOf(intentGuess.confidence()),
                "基于关键词的最小闭环 mock 识别");
    }

    private void insertRiskDecision(String traceId, String sessionId, String messageId, String intent, RuleDecision decision) {
        jdbcTemplate.update(
                """
                INSERT INTO risk_decision (
                    trace_id, session_id, message_id, intent, risk_level, route_decision, reason_code, reason, matched_rule_ids, details
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON))
                """,
                traceId,
                sessionId,
                messageId,
                intent,
                decision.riskLevel().name(),
                decision.routeDecision().name(),
                decision.reasonCode(),
                decision.reason(),
                json(List.of(decision.reasonCode())),
                json(Map.of("mock", true)));
    }

    private String createWorkOrder(String traceId, String sessionId, String userId, String intent, RuleDecision decision) {
        String ticketId = "wo_" + UUID.randomUUID();
        String priority = decision.routeDecision() == RouteDecision.HUMAN_TAKEOVER ? "HIGH" : "NORMAL";
        jdbcTemplate.update(
                """
                INSERT INTO work_order (
                    ticket_id, trace_id, session_id, user_id, intent, risk_level, route_decision,
                    status, priority, reason, context_snapshot, sla_deadline
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, CAST(? AS JSON), ?)
                """,
                ticketId,
                traceId,
                sessionId,
                userId,
                intent,
                decision.riskLevel().name(),
                decision.routeDecision().name(),
                priority,
                decision.reason(),
                json(Map.of("mock", true, "reasonCode", decision.reasonCode())),
                timestamp(Instant.now().plus(2, ChronoUnit.HOURS)));
        LOGGER.info(
                "Agent创建人工工单 traceId={} sessionId={} ticketId={} userId={} intent={} routeDecision={} riskLevel={}",
                traceId,
                sessionId,
                ticketId,
                userId,
                intent,
                decision.routeDecision(),
                decision.riskLevel());
        return ticketId;
    }

    private void createWorkOrderAction(String ticketId, String traceId, String operatorId, String actionType, String comment) {
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
                json(Map.of("mock", true)));
    }

    private void createApprovalTask(
            String ticketId,
            String traceId,
            String sessionId,
            String userId,
            String intent,
            RuleDecision decision,
            String content) {
        String approvalId = "ap_" + UUID.randomUUID();
        String approvalType = approvalType(intent);
        jdbcTemplate.update(
                """
                INSERT INTO approval_task (
                    approval_id, ticket_id, trace_id, session_id, user_id, intent, approval_type,
                    risk_level, route_decision, status, priority, risk_reason, request_payload,
                    context_snapshot, expire_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'NORMAL', ?, CAST(? AS JSON), CAST(? AS JSON), ?)
                """,
                approvalId,
                ticketId,
                traceId,
                sessionId,
                userId,
                intent,
                approvalType,
                decision.riskLevel().name(),
                decision.routeDecision().name(),
                decision.reason(),
                json(Map.of("content", content)),
                json(Map.of("mock", true, "ticketId", ticketId)),
                timestamp(Instant.now().plus(2, ChronoUnit.HOURS)));
        jdbcTemplate.update(
                """
                INSERT INTO approval_action (
                    action_id, approval_id, ticket_id, trace_id, operator_id, action_type,
                    before_status, after_status, comment, action_data
                ) VALUES (?, ?, ?, ?, 'system', 'CREATE', NULL, 'PENDING', ?, CAST(? AS JSON))
                """,
                "aa_" + UUID.randomUUID(),
                approvalId,
                ticketId,
                traceId,
                decision.reason(),
                json(Map.of("mock", true)));
        LOGGER.info(
                "Agent创建审批任务 traceId={} sessionId={} ticketId={} approvalId={} userId={} intent={} approvalType={}",
                traceId,
                sessionId,
                ticketId,
                approvalId,
                userId,
                intent,
                approvalType);
    }

    private void createHumanTakeover(
            String ticketId,
            String traceId,
            String sessionId,
            String userId,
            RuleDecision decision,
            String content) {
        jdbcTemplate.update(
                """
                INSERT INTO human_takeover (
                    takeover_id, ticket_id, trace_id, session_id, user_id, trigger_source,
                    status, priority, reason, context_snapshot
                ) VALUES (?, ?, ?, ?, ?, ?, 'REQUESTED', 'HIGH', ?, CAST(? AS JSON))
                """,
                "ht_" + UUID.randomUUID(),
                ticketId,
                traceId,
                sessionId,
                userId,
                takeoverSource(decision.reasonCode()),
                decision.reason(),
                json(Map.of("mock", true, "content", content)));
        LOGGER.info(
                "Agent创建人工接管记录 traceId={} sessionId={} ticketId={} userId={} reasonCode={}",
                traceId,
                sessionId,
                ticketId,
                userId,
                decision.reasonCode());
    }

    private SkillExecutionRequest buildSkillExecutionRequest(
            String traceId,
            String sessionId,
            String messageId,
            String userId,
            String channel,
            String content,
            IntentGuess intentGuess,
            SkillConfig skillConfig,
            RuleDecision decision,
            Map<String, Object> requestMetadata) {
        Map<String, Object> context = new LinkedHashMap<>();
        // 当前还没有真实订单参数抽取，只传会话上下文，避免提前编造业务字段。
        context.put("channel", channel);
        context.put("content", content);
        context.put("confidence", intentGuess.confidence());
        context.put("mock", true);
        if (requestMetadata != null && !requestMetadata.isEmpty()) {
            context.put("requestMetadata", requestMetadata);
        }
        Map<String, Object> parameters = buildSkillParameters(intentGuess.intent(), content);
        return new SkillExecutionRequest(
                traceId,
                sessionId,
                messageId,
                userId,
                intentGuess.intent(),
                skillConfig.skillId(),
                decision.riskLevel(),
                decision.routeDecision(),
                parameters,
                context);
    }

    private Map<String, Object> buildSkillParameters(String intent, String content) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        // 当前只提取稳定、低风险的订单号；地址、退款原因等复杂槽位后续由专门 NLU/表单补齐。
        if ("order.query".equals(intent) || "order.modify_address".equals(intent) || "logistics.query".equals(intent)) {
            String orderNo = extractOrderNo(content);
            if (orderNo != null && !orderNo.isBlank()) {
                parameters.put("orderNo", orderNo);
            }
        }
        return parameters;
    }

    private String extractOrderNo(String content) {
        Matcher matcher = ORDER_NO_PATTERN.matcher(content == null ? "" : content);
        return matcher.find() ? matcher.group(1) : "";
    }

    private void insertSkillExecutionLog(
            String traceId,
            String sessionId,
            String messageId,
            String userId,
            String intent,
            SkillConfig skillConfig,
            RuleDecision decision) {
        String status = switch (decision.routeDecision()) {
            case HUMAN_REVIEW -> "REVIEW_REQUIRED";
            case HUMAN_TAKEOVER, REJECT -> "CANCELLED";
            case CONFIRM_BEFORE_EXECUTE -> "PENDING";
            case AUTO_REPLY, AUTO_EXECUTE -> "SUCCEEDED";
        };
        Instant now = Instant.now();
        Timestamp finishedAt = "PENDING".equals(status) ? null : timestamp(now);
        jdbcTemplate.update(
                """
                INSERT INTO skill_execution_log (
                    execution_id, trace_id, session_id, message_id, skill_id, intent, user_id,
                    risk_level, route_decision, status, request_snapshot, step_results, response_snapshot,
                    started_at, finished_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), ?, ?)
                """,
                "se_" + UUID.randomUUID(),
                traceId,
                sessionId,
                messageId,
                skillConfig.skillId(),
                intent,
                userId,
                decision.riskLevel().name(),
                decision.routeDecision().name(),
                status,
                json(Map.of("mock", true)),
                json(List.of()),
                json(Map.of("decision", decision.routeDecision().name())),
                timestamp(now),
                finishedAt);
        LOGGER.info(
                "Agent写入本地技能执行日志 traceId={} sessionId={} messageId={} skillId={} status={}",
                traceId,
                sessionId,
                messageId,
                skillConfig.skillId(),
                status);
    }

    private AgentReply buildReply(
            String traceId,
            String sessionId,
            IntentGuess intentGuess,
            RuleDecision decision,
            String ticketId,
            SkillConfig skillConfig,
            SkillExecutionResult skillExecution,
            FaqQueryResult faqResult) {
        String skillMessage = skillExecutionMessage(skillExecution);
        String knowledgeMessage = knowledgeAnswerMessage(faqResult);
        String autoReplyMessage = textOr(knowledgeMessage, skillMessage);
        String content = switch (decision.routeDecision()) {
            case AUTO_REPLY -> textOr(
                    autoReplyMessage,
                    "我识别到你的需求是「" + intentGuess.intent() + "」。当前已完成最小闭环，真实业务系统稍后接入。");
            case AUTO_EXECUTE -> textOr(
                    skillMessage,
                    "我识别到你的需求是「" + intentGuess.intent() + "」。该技能当前可自动处理，真实电商 API 尚未接入。");
            case CONFIRM_BEFORE_EXECUTE -> "该操作需要你确认后再继续。当前最小闭环已记录风控决策。";
            case HUMAN_REVIEW -> "这个请求需要人工审核，我已创建工单" + suffixTicket(ticketId) + "。";
            case HUMAN_TAKEOVER -> "我已为你转人工处理" + suffixTicket(ticketId) + "，请稍等。";
            case REJECT -> "当前请求暂时无法自动处理。";
        };
        List<QuickAction> quickActions = decision.routeDecision() == RouteDecision.CONFIRM_BEFORE_EXECUTE
                ? List.of(
                        new QuickAction("确认继续", "confirm", "CONFIRM", Map.of()),
                        new QuickAction("取消", "cancel", "CANCEL", Map.of()))
                : List.of();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("intent", intentGuess.intent());
        metadata.put("confidence", intentGuess.confidence());
        metadata.put("skillId", skillConfig == null ? "" : skillConfig.skillId());
        Object mock = true;
        if (skillExecution != null) {
            metadata.put("skillExecutionId", skillExecution.executionId());
            metadata.put("skillExecutionStatus", skillExecution.status());
            if (skillExecution.response() != null && skillExecution.response().containsKey("mock")) {
                mock = skillExecution.response().get("mock");
            }
        }
        metadata.put("mock", mock);
        if (faqResult != null) {
            metadata.put("knowledgeAnswerId", faqResult.answerId());
            metadata.put("knowledgeMatched", faqResult.matched());
            metadata.put("knowledgeConfidence", faqResult.confidence());
            metadata.put("knowledgeSource", faqResult.source());
            metadata.put("matchedKeywords", faqResult.matchedKeywords() == null ? List.of() : faqResult.matchedKeywords());
        }
        return new AgentReply(
                "r_" + UUID.randomUUID(),
                traceId,
                sessionId,
                MessageType.TEXT,
                content,
                quickActions,
                decision.riskLevel(),
                decision.routeDecision(),
                ticketId,
                metadata,
                Instant.now());
    }

    private void insertAgentMessage(AgentReply reply, String userId) {
        jdbcTemplate.update(
                """
                INSERT INTO cs_message (
                    message_id, trace_id, session_id, user_id, role, message_type, content,
                    quick_actions, intent, risk_level, route_decision, metadata
                ) VALUES (?, ?, ?, ?, 'AGENT', ?, ?, CAST(? AS JSON), ?, ?, ?, CAST(? AS JSON))
                """,
                reply.replyId(),
                reply.traceId(),
                reply.sessionId(),
                userId,
                reply.messageType().name(),
                reply.content(),
                json(reply.quickActions()),
                reply.metadata().get("intent"),
                reply.riskLevel().name(),
                reply.routeDecision().name(),
                json(reply.metadata()));
    }

    private boolean requiresHuman(RouteDecision routeDecision) {
        return routeDecision == RouteDecision.HUMAN_REVIEW || routeDecision == RouteDecision.HUMAN_TAKEOVER;
    }

    private boolean canCallSkillEngine(RouteDecision routeDecision) {
        return routeDecision == RouteDecision.AUTO_REPLY || routeDecision == RouteDecision.AUTO_EXECUTE;
    }

    private boolean canCallKnowledge(String intent, RouteDecision routeDecision) {
        return "faq.query".equals(intent) && routeDecision == RouteDecision.AUTO_REPLY;
    }

    private String skillExecutionMessage(SkillExecutionResult skillExecution) {
        if (skillExecution == null) {
            return null;
        }
        if (skillExecution.response() != null) {
            Object summary = skillExecution.response().get("summary");
            if (summary != null && !summary.toString().isBlank()) {
                return summary.toString();
            }
        }
        return skillExecution.message();
    }

    private String knowledgeAnswerMessage(FaqQueryResult faqResult) {
        return faqResult == null ? null : faqResult.answer();
    }

    private boolean hasHighAmount(String content) {
        String amountText = ORDER_NO_PATTERN.matcher(content == null ? "" : content).replaceAll(" ");
        Matcher matcher = AMOUNT_PATTERN.matcher(amountText);
        while (matcher.find()) {
            try {
                BigDecimal amount = new BigDecimal(matcher.group(1));
                if (amount.compareTo(new BigDecimal("500.00")) >= 0) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private String approvalType(String intent) {
        return switch (intent) {
            case "refund.apply" -> "REFUND";
            case "exchange.apply" -> "EXCHANGE";
            case "order.modify_address" -> "ADDRESS_CHANGE";
            default -> "OTHER";
        };
    }

    private String takeoverSource(String reasonCode) {
        if ("R003_LOW_CONFIDENCE_TAKEOVER".equals(reasonCode)) {
            return "AGENT_LOW_CONFIDENCE";
        }
        if ("R004_NEGATIVE_EMOTION_TAKEOVER".equals(reasonCode)) {
            return "RISK_RULE";
        }
        return "RISK_RULE";
    }

    private String suffixTicket(String ticketId) {
        return ticketId == null ? "" : "（" + ticketId + "）";
    }

    private int contentLength(String content) {
        return content == null ? 0 : content.length();
    }

    private boolean isFaqQuestion(String lower) {
        boolean looksLikeQuestion = containsAny(
                lower,
                "faq",
                "规则",
                "政策",
                "说明",
                "怎么",
                "如何",
                "多久",
                "几天",
                "条件",
                "流程",
                "标准",
                "能不能",
                "可以吗",
                "怎么算",
                "是什么");
        boolean hasKnowledgeTopic = containsAny(
                lower,
                "退款",
                "退货",
                "换货",
                "售后",
                "发票",
                "开票",
                "运费",
                "配送费",
                "邮费",
                "保价",
                "价保",
                "价格保护",
                "地址");
        return looksLikeQuestion && hasKnowledgeTopic;
    }

    private boolean containsAny(String value, String... needles) {
        String normalized = value == null ? "" : value;
        for (String needle : needles) {
            if (normalized.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize JSON payload", e);
        }
    }

    private record IntentGuess(String intent, double confidence) {
    }

    private record SkillConfig(String skillId, String intent, RiskLevel baseRiskLevel, String executionType) {
    }

    private record RiskRule(
            String ruleId,
            RiskLevel riskLevel,
            RouteDecision routeDecision,
            int priority,
            String description) {
    }

    private record RuleDecision(
            RiskLevel riskLevel,
            RouteDecision routeDecision,
            String reasonCode,
            String reason) {
    }

    private record SessionSnapshot(
            String sessionId,
            String traceId,
            String userId,
            String channel,
            String status,
            String dialogState,
            String currentIntent) {
    }
}
