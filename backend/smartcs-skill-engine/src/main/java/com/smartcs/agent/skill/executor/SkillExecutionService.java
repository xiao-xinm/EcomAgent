package com.smartcs.agent.skill.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.common.enums.ErrorCode;
import com.smartcs.agent.common.enums.RiskLevel;
import com.smartcs.agent.common.enums.RouteDecision;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.skill.definition.SkillDtos.SkillDefinitionView;
import com.smartcs.agent.skill.definition.SkillDtos.SkillExecuteRequest;
import com.smartcs.agent.skill.definition.SkillDtos.SkillExecuteResult;
import com.smartcs.agent.skill.definition.SkillDtos.SkillSlotView;
import com.smartcs.agent.skill.definition.SkillDtos.SkillStepResult;
import com.smartcs.agent.skill.definition.SkillDtos.SkillStepView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 技能执行服务：读取声明式技能配置，执行当前阶段的最小 mock 技能，并写执行日志。
 */
@Service
public class SkillExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillExecutionService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SkillExecutionService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<SkillDefinitionView> listSkills(String intent, String status, Boolean enabled) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT skill_id, skill_name, intent, base_risk_level, execution_type, enabled, status,
                       description, pre_conditions, retry_policy, timeout_ms, owner, version,
                       created_at, updated_at
                FROM skill_registry
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (hasText(intent)) {
            sql.append(" AND intent = ?");
            params.add(intent);
        }
        if (hasText(status)) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (enabled != null) {
            sql.append(" AND enabled = ?");
            params.add(enabled ? 1 : 0);
        }
        sql.append(" ORDER BY intent, version DESC");

        List<SkillDefinitionView> records =
                jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapSkillDefinition(rs), params.toArray());
        LOGGER.info(
                "查询技能列表 intent={} status={} enabled={} count={}",
                intent,
                status,
                enabled,
                records.size());
        return records;
    }

    public SkillDefinitionView getSkill(String skillId) {
        SkillDefinitionView skill = findSkillById(skillId, false);
        if (skill == null) {
            throw new SkillOperationException(ErrorCode.SKILL_NOT_FOUND, "Skill not found: " + skillId);
        }
        LOGGER.info("查询技能详情 skillId={} intent={} status={}", skill.skillId(), skill.intent(), skill.status());
        return skill;
    }

    @Transactional
    public SkillExecuteResult execute(SkillExecuteRequest request) {
        LOGGER.info(
                "Skill Engine执行开始 traceId={} sessionId={} messageId={} userId={} skillId={} intent={}",
                request.traceId(),
                request.sessionId(),
                request.messageId(),
                request.userId(),
                request.skillId(),
                request.intent());
        SkillDefinitionView skill = findExecutableSkill(request);
        if (skill == null) {
            throw new SkillOperationException(ErrorCode.SKILL_NOT_FOUND, "No active skill matches request");
        }

        String traceId = textOr(request.traceId(), TraceIds.newTraceId());
        RiskLevel riskLevel = request.riskLevel() == null ? skill.baseRiskLevel() : request.riskLevel();
        RouteDecision routeDecision = request.routeDecision() == null
                ? defaultRouteDecision(skill)
                : request.routeDecision();
        String status = resolveExecutionStatus(skill, routeDecision);
        String executionId = "se_" + UUID.randomUUID();
        Instant startedAt = Instant.now();
        Instant finishedAt = "PENDING".equals(status) ? null : Instant.now();
        Map<String, Object> response = buildMockResponse(skill, request, status);
        List<SkillStepResult> stepResults = buildStepResults(skill, status);
        String message = buildMessage(skill, status, routeDecision);
        LOGGER.info(
                "Skill Engine执行决策 traceId={} executionId={} skillId={} intent={} riskLevel={} routeDecision={} status={} stepCount={}",
                traceId,
                executionId,
                skill.skillId(),
                skill.intent(),
                riskLevel,
                routeDecision,
                status,
                stepResults.size());

        SkillExecuteResult result = new SkillExecuteResult(
                executionId,
                traceId,
                request.sessionId(),
                request.messageId(),
                skill.skillId(),
                skill.skillName(),
                skill.intent(),
                riskLevel,
                routeDecision,
                status,
                response,
                stepResults,
                message,
                startedAt,
                finishedAt);
        insertExecutionLog(request, result);
        return result;
    }

    private SkillDefinitionView findExecutableSkill(SkillExecuteRequest request) {
        // Agent Core 优先传 skillId；如果没有，再按 intent 找最新可用技能。
        if (hasText(request.skillId())) {
            return findSkillById(request.skillId(), true);
        }
        if (!hasText(request.intent())) {
            return null;
        }
        List<SkillDefinitionView> skills = jdbcTemplate.query(
                """
                SELECT skill_id, skill_name, intent, base_risk_level, execution_type, enabled, status,
                       description, pre_conditions, retry_policy, timeout_ms, owner, version,
                       created_at, updated_at
                FROM skill_registry
                WHERE intent = ? AND enabled = 1 AND status = 'ACTIVE'
                ORDER BY version DESC
                LIMIT 1
                """,
                (rs, rowNum) -> mapSkillDefinition(rs),
                request.intent());
        return skills.stream().findFirst().orElse(null);
    }

    private SkillDefinitionView findSkillById(String skillId, boolean onlyActive) {
        if (!hasText(skillId)) {
            return null;
        }
        String activeClause = onlyActive ? " AND enabled = 1 AND status = 'ACTIVE'" : "";
        List<SkillDefinitionView> skills = jdbcTemplate.query(
                """
                SELECT skill_id, skill_name, intent, base_risk_level, execution_type, enabled, status,
                       description, pre_conditions, retry_policy, timeout_ms, owner, version,
                       created_at, updated_at
                FROM skill_registry
                WHERE skill_id = ?
                """
                        + activeClause,
                (rs, rowNum) -> mapSkillDefinition(rs),
                skillId);
        return skills.stream().findFirst().orElse(null);
    }

    private SkillDefinitionView mapSkillDefinition(ResultSet rs) throws SQLException {
        String skillId = rs.getString("skill_id");
        return new SkillDefinitionView(
                skillId,
                rs.getString("skill_name"),
                rs.getString("intent"),
                RiskLevel.valueOf(rs.getString("base_risk_level")),
                rs.getString("execution_type"),
                rs.getBoolean("enabled"),
                rs.getString("status"),
                rs.getString("description"),
                readJson(rs.getString("pre_conditions")),
                readJson(rs.getString("retry_policy")),
                rs.getInt("timeout_ms"),
                rs.getString("owner"),
                rs.getString("version"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                loadSlots(skillId),
                loadSteps(skillId));
    }

    private List<SkillSlotView> loadSlots(String skillId) {
        return jdbcTemplate.query(
                """
                SELECT slot_id, skill_id, slot_name, slot_type, required, source_priority,
                       enum_values, validation_rule, clarification_template, default_value, display_order
                FROM skill_slot
                WHERE skill_id = ?
                ORDER BY display_order, slot_id
                """,
                (rs, rowNum) -> new SkillSlotView(
                        rs.getLong("slot_id"),
                        rs.getString("skill_id"),
                        rs.getString("slot_name"),
                        rs.getString("slot_type"),
                        rs.getBoolean("required"),
                        readJson(rs.getString("source_priority")),
                        readJson(rs.getString("enum_values")),
                        rs.getString("validation_rule"),
                        rs.getString("clarification_template"),
                        readJson(rs.getString("default_value")),
                        rs.getInt("display_order")),
                skillId);
    }

    private List<SkillStepView> loadSteps(String skillId) {
        return jdbcTemplate.query(
                """
                SELECT step_id, skill_id, step_no, step_name, api_name, http_method, endpoint,
                       params_mapping, headers_mapping, body_template, result_mapping, required,
                       rollback_endpoint, rollback_mapping, timeout_ms, retry_policy
                FROM skill_api_step
                WHERE skill_id = ?
                ORDER BY step_no
                """,
                (rs, rowNum) -> new SkillStepView(
                        rs.getLong("step_id"),
                        rs.getString("skill_id"),
                        rs.getInt("step_no"),
                        rs.getString("step_name"),
                        rs.getString("api_name"),
                        rs.getString("http_method"),
                        rs.getString("endpoint"),
                        readJson(rs.getString("params_mapping")),
                        readJson(rs.getString("headers_mapping")),
                        readJson(rs.getString("body_template")),
                        readJson(rs.getString("result_mapping")),
                        rs.getBoolean("required"),
                        rs.getString("rollback_endpoint"),
                        readJson(rs.getString("rollback_mapping")),
                        rs.getInt("timeout_ms"),
                        readJson(rs.getString("retry_policy"))),
                skillId);
    }

    private RouteDecision defaultRouteDecision(SkillDefinitionView skill) {
        if ("REVIEW_ONLY".equals(skill.executionType())) {
            return RouteDecision.HUMAN_REVIEW;
        }
        return switch (skill.baseRiskLevel()) {
            case L0 -> RouteDecision.AUTO_REPLY;
            case L1 -> RouteDecision.AUTO_EXECUTE;
            case L2 -> RouteDecision.CONFIRM_BEFORE_EXECUTE;
            case L3 -> RouteDecision.HUMAN_REVIEW;
        };
    }

    private String resolveExecutionStatus(SkillDefinitionView skill, RouteDecision routeDecision) {
        if ("REVIEW_ONLY".equals(skill.executionType())) {
            return "REVIEW_REQUIRED";
        }
        return switch (routeDecision) {
            case HUMAN_REVIEW -> "REVIEW_REQUIRED";
            case HUMAN_TAKEOVER, REJECT -> "CANCELLED";
            case CONFIRM_BEFORE_EXECUTE -> "PENDING";
            case AUTO_REPLY, AUTO_EXECUTE -> "SUCCEEDED";
        };
    }

    private Map<String, Object> buildMockResponse(
            SkillDefinitionView skill,
            SkillExecuteRequest request,
            String status) {
        // 当前阶段只返回 mock 业务结果；真实电商 API 接入后，这里会替换为声明式步骤执行结果。
        Map<String, Object> response = new LinkedHashMap<>();
        if ("order.query".equals(skill.intent())) {
            return buildOrderQueryResponse(skill, request, status);
        }

        response.put("mock", true);
        response.put("skillId", skill.skillId());
        response.put("intent", skill.intent());
        response.put("status", status);

        if ("order.modify_address".equals(skill.intent())) {
            response.put("orderId", textFrom(request.parameters(), "order_id", "orderId", "mock-order"));
            response.put("modifyRequestId", "addr_" + UUID.randomUUID());
            response.put("summary", "已记录改地址请求，真实电商订单 API 后续接入。");
            return response;
        }

        response.put("reviewRequired", "REVIEW_REQUIRED".equals(status));
        response.put("summary", "该技能已进入人工审核或人工兜底流程。");
        return response;
    }

    private Map<String, Object> buildOrderQueryResponse(
            SkillDefinitionView skill,
            SkillExecuteRequest request,
            String status) {
        if (!"SUCCEEDED".equals(status)) {
            return mockOrderQueryResponse(skill, request, status);
        }

        try {
            Map<String, Object> response = baseSkillResponse(skill, status);
            response.put("mock", false);
            response.put("source", "ecom_order");

            OrderSnapshot order = findOrderForQuery(request);
            response.put("found", order != null);
            if (order == null) {
                response.put("summary", "暂未查询到你的订单，请补充订单号或联系人工客服。");
                return response;
            }

            List<Map<String, Object>> items = listOrderItems(order.orderId()).stream()
                    .map(this::itemResponse)
                    .toList();
            response.put("orderId", order.orderId());
            response.put("orderNo", order.orderNo());
            response.put("orderStatus", order.orderStatus());
            response.put("payStatus", order.payStatus());
            response.put("logisticsStatus", order.logisticsStatus());
            response.put("totalAmount", order.totalAmount());
            response.put("paidAmount", order.paidAmount());
            response.put("currency", order.currency());
            response.put("canModifyAddress", order.canModifyAddress());
            response.put("items", items);
            response.put("summary", orderQuerySummary(order, items));
            return response;
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "查询电商订单失败，回退到mock响应 traceId={} sessionId={} userId={}",
                    request.traceId(),
                    request.sessionId(),
                    request.userId(),
                    exception);
            Map<String, Object> response = mockOrderQueryResponse(skill, request, status);
            response.put("fallbackReason", "ORDER_DOMAIN_QUERY_FAILED");
            return response;
        }
    }

    private OrderSnapshot findOrderForQuery(SkillExecuteRequest request) {
        String userId = request.userId();
        if (!hasText(userId)) {
            return null;
        }

        String orderId = firstText(request.parameters(), "order_id", "orderId");
        String orderNo = firstText(request.parameters(), "order_no", "orderNo");
        StringBuilder sql = new StringBuilder(
                """
                SELECT order_id, order_no, user_id, order_status, pay_status, logistics_status,
                       total_amount, paid_amount, currency, can_modify_address, created_at
                FROM ecom_order
                WHERE user_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (hasText(orderId)) {
            sql.append(" AND order_id = ?");
            params.add(orderId);
        }
        if (hasText(orderNo)) {
            sql.append(" AND order_no = ?");
            params.add(orderNo);
        }
        sql.append(" ORDER BY created_at DESC LIMIT 1");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapOrderSnapshot(rs), params.toArray()).stream()
                .findFirst()
                .orElse(null);
    }

    private List<OrderItemSnapshot> listOrderItems(String orderId) {
        return jdbcTemplate.query(
                """
                SELECT item_id, sku_id, sku_name, quantity, unit_price, total_amount, after_sale_status
                FROM ecom_order_item
                WHERE order_id = ?
                ORDER BY item_id
                """,
                (rs, rowNum) -> new OrderItemSnapshot(
                        rs.getString("item_id"),
                        rs.getString("sku_id"),
                        rs.getString("sku_name"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("total_amount"),
                        rs.getString("after_sale_status")),
                orderId);
    }

    private OrderSnapshot mapOrderSnapshot(ResultSet rs) throws SQLException {
        return new OrderSnapshot(
                rs.getString("order_id"),
                rs.getString("order_no"),
                rs.getString("user_id"),
                rs.getString("order_status"),
                rs.getString("pay_status"),
                rs.getString("logistics_status"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("paid_amount"),
                rs.getString("currency"),
                rs.getBoolean("can_modify_address"),
                toInstant(rs.getTimestamp("created_at")));
    }

    private Map<String, Object> itemResponse(OrderItemSnapshot item) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("itemId", item.itemId());
        response.put("skuId", item.skuId());
        response.put("skuName", item.skuName());
        response.put("quantity", item.quantity());
        response.put("unitPrice", item.unitPrice());
        response.put("totalAmount", item.totalAmount());
        response.put("afterSaleStatus", item.afterSaleStatus());
        return response;
    }

    private String orderQuerySummary(OrderSnapshot order, List<Map<String, Object>> items) {
        String itemName = items.isEmpty() ? "商品" : String.valueOf(items.get(0).get("skuName"));
        return "已查询到订单 " + order.orderNo()
                + "，商品：" + itemName
                + "，订单状态：" + order.orderStatus()
                + "，物流状态：" + order.logisticsStatus()
                + "。";
    }

    private Map<String, Object> mockOrderQueryResponse(
            SkillDefinitionView skill,
            SkillExecuteRequest request,
            String status) {
        Map<String, Object> response = baseSkillResponse(skill, status);
        response.put("mock", true);
        response.put("orderId", textFrom(request.parameters(), "order_id", "orderId", "latest"));
        response.put("orderStatus", "SHIPPED");
        response.put("logisticsStatus", "IN_TRANSIT");
        response.put("summary", "已为你查到最近订单，当前订单已发货，物流运输中。");
        return response;
    }

    private Map<String, Object> baseSkillResponse(SkillDefinitionView skill, String status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("skillId", skill.skillId());
        response.put("intent", skill.intent());
        response.put("status", status);
        return response;
    }

    private List<SkillStepResult> buildStepResults(SkillDefinitionView skill, String status) {
        if (!"SUCCEEDED".equals(status)) {
            return List.of();
        }
        return skill.steps().stream()
                .map(step -> {
                    Map<String, Object> request = new LinkedHashMap<>();
                    request.put("method", step.httpMethod());
                    request.put("endpoint", step.endpoint());

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("mock", true);
                    response.put("apiName", step.apiName());

                    return new SkillStepResult(
                            step.stepNo(),
                            step.stepName(),
                            step.apiName(),
                            "SUCCEEDED",
                            request,
                            response,
                            "mock step completed");
                })
                .toList();
    }

    private String buildMessage(SkillDefinitionView skill, String status, RouteDecision routeDecision) {
        return switch (status) {
            case "SUCCEEDED" -> "order.query".equals(skill.intent())
                    ? "已完成订单查询。"
                    : "已完成技能执行，真实业务 API 后续接入。";
            case "REVIEW_REQUIRED" -> "该请求需要人工审核，Agent 不直接执行敏感操作。";
            case "PENDING" -> "该请求需要用户确认后再执行。";
            case "CANCELLED" -> "该技能本次未执行，路由决策为 " + routeDecision.name() + "。";
            default -> "技能执行状态：" + status;
        };
    }

    private void insertExecutionLog(SkillExecuteRequest request, SkillExecuteResult result) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO skill_execution_log (
                        execution_id, trace_id, session_id, message_id, skill_id, intent, user_id,
                        risk_level, route_decision, status, request_snapshot, step_results, response_snapshot,
                        started_at, finished_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), ?, ?)
                    """,
                    result.executionId(),
                    result.traceId(),
                    result.sessionId(),
                    result.messageId(),
                    result.skillId(),
                    result.intent(),
                    request.userId(),
                    result.riskLevel().name(),
                    result.routeDecision().name(),
                    result.status(),
                    json(requestSnapshot(request)),
                    json(result.stepResults()),
                    json(result.response()),
                    timestamp(result.startedAt()),
                    timestamp(result.finishedAt()));
            LOGGER.info(
                    "Skill Engine执行日志已落库 traceId={} executionId={} skillId={} status={}",
                    result.traceId(),
                    result.executionId(),
                    result.skillId(),
                    result.status());
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "Skill Engine执行日志落库失败 traceId={} executionId={} skillId={} status={}",
                    result.traceId(),
                    result.executionId(),
                    result.skillId(),
                    result.status(),
                    exception);
            throw new SkillOperationException(
                    ErrorCode.SKILL_EXECUTION_FAILED,
                    "Unable to persist skill execution log: " + exception.getMessage());
        }
    }

    private Map<String, Object> requestSnapshot(SkillExecuteRequest request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("intent", request.intent());
        snapshot.put("skillId", request.skillId());
        snapshot.put("riskLevel", request.riskLevel() == null ? "" : request.riskLevel().name());
        snapshot.put("routeDecision", request.routeDecision() == null ? "" : request.routeDecision().name());
        snapshot.put("parameters", request.parameters() == null ? Map.of() : request.parameters());
        snapshot.put("context", request.context() == null ? Map.of() : request.context());
        return snapshot;
    }

    private Object readJson(String json) {
        if (!hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException exception) {
            return Map.of("raw", json);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize JSON payload", exception);
        }
    }

    private String textFrom(Map<String, Object> values, String snakeKey, String camelKey, String fallback) {
        if (values == null) {
            return fallback;
        }
        Object value = values.get(snakeKey);
        if (value == null) {
            value = values.get(camelKey);
        }
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private String firstText(Map<String, Object> values, String... keys) {
        if (values == null) {
            return "";
        }
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return "";
    }

    private String textOr(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private record OrderSnapshot(
            String orderId,
            String orderNo,
            String userId,
            String orderStatus,
            String payStatus,
            String logisticsStatus,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            String currency,
            boolean canModifyAddress,
            Instant createdAt) {}

    private record OrderItemSnapshot(
            String itemId,
            String skuId,
            String skuName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            String afterSaleStatus) {}
}
