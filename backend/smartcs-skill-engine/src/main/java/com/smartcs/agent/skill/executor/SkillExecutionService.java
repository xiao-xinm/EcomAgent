package com.smartcs.agent.skill.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.common.enums.ErrorCode;
import com.smartcs.agent.common.enums.RiskLevel;
import com.smartcs.agent.common.enums.RouteDecision;
import com.smartcs.agent.common.observability.LogFields;
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
                "Skill Engine执行开始 {} messageId={} skillId={} intent={}",
                LogFields.chat(request.traceId(), request.sessionId(), request.userId()),
                LogFields.value(request.messageId()),
                LogFields.value(request.skillId()),
                LogFields.value(request.intent()));
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
                "Skill Engine执行决策 {} executionId={} skillId={} intent={} riskLevel={} routeDecision={} status={} stepCount={}",
                LogFields.chat(traceId, request.sessionId(), request.userId()),
                LogFields.value(executionId),
                LogFields.value(skill.skillId()),
                LogFields.value(skill.intent()),
                LogFields.value(riskLevel),
                LogFields.value(routeDecision),
                LogFields.value(status),
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
        if ("logistics.query".equals(skill.intent())) {
            return buildLogisticsQueryResponse(skill, request, status);
        }
        if ("order.modify_address".equals(skill.intent())) {
            return buildModifyAddressResponse(skill, request, status);
        }
        if ("order.cancel".equals(skill.intent())) {
            return buildOrderCancelResponse(skill, request, status);
        }

        response.put("mock", true);
        response.put("skillId", skill.skillId());
        response.put("intent", skill.intent());
        response.put("status", status);

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
                    "查询电商订单失败，回退到mock响应 {}",
                    LogFields.chat(request.traceId(), request.sessionId(), request.userId()),
                    exception);
            Map<String, Object> response = mockOrderQueryResponse(skill, request, status);
            response.put("fallbackReason", "ORDER_DOMAIN_QUERY_FAILED");
            return response;
        }
    }

    private Map<String, Object> buildModifyAddressResponse(
            SkillDefinitionView skill,
            SkillExecuteRequest request,
            String status) {
        if (!"SUCCEEDED".equals(status)) {
            return mockModifyAddressResponse(skill, request, status);
        }

        try {
            Map<String, Object> response = baseSkillResponse(skill, status);
            response.put("mock", false);
            response.put("source", "ecom_order");

            OrderSnapshot order = findOrderForQuery(request);
            response.put("found", order != null);
            if (order == null) {
                response.put("applied", false);
                response.put("summary", "暂未查询到可修改地址的订单，请补充订单号或联系人工客服。");
                return response;
            }

            response.put("orderId", order.orderId());
            response.put("orderNo", order.orderNo());
            response.put("canModifyAddress", order.canModifyAddress());
            if (!canApplyAddressModify(order)) {
                response.put("applied", false);
                response.put("requiresHuman", true);
                response.put("summary", "当前订单状态不支持自动修改地址，请转人工处理。");
                return response;
            }

            AddressPayload address = readAddressPayload(request.parameters());
            List<String> missingFields = missingAddressFields(address);
            if (!missingFields.isEmpty()) {
                response.put("applied", false);
                response.put("missingFields", missingFields);
                response.put("summary", "请补充完整的新收货地址后再确认。");
                return response;
            }

            String addressId = "addr_" + UUID.randomUUID();
            applyAddressModify(order, address, addressId, request.messageId());
            response.put("applied", true);
            response.put("modifyRequestId", addressId);
            response.put("newAddress", addressResponse(address));
            response.put("summary", "已为订单 " + order.orderNo() + " 修改收货地址。");
            return response;
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "修改电商订单地址失败，回退到mock响应 {}",
                    LogFields.chat(request.traceId(), request.sessionId(), request.userId()),
                    exception);
            Map<String, Object> response = mockModifyAddressResponse(skill, request, status);
            response.put("fallbackReason", "ORDER_ADDRESS_MODIFY_FAILED");
            return response;
        }
    }

    private Map<String, Object> buildLogisticsQueryResponse(
            SkillDefinitionView skill,
            SkillExecuteRequest request,
            String status) {
        if (!"SUCCEEDED".equals(status)) {
            return mockLogisticsQueryResponse(skill, request, status);
        }

        try {
            Map<String, Object> response = baseSkillResponse(skill, status);
            response.put("mock", false);
            response.put("source", "ecom_order");

            OrderSnapshot order = findOrderForQuery(request);
            response.put("found", order != null);
            if (order == null) {
                response.put("summary", "暂未查询到订单物流信息，请补充订单号或联系人工客服。");
                return response;
            }

            response.put("orderId", order.orderId());
            response.put("orderNo", order.orderNo());
            response.put("orderStatus", order.orderStatus());
            response.put("logisticsStatus", order.logisticsStatus());
            response.put("carrier", "顺丰速运");
            response.put("trackingNo", trackingNo(order));
            response.put("latestNode", logisticsNode(order.logisticsStatus()));
            response.put("estimatedDeliveryTime", estimatedDeliveryTime(order.logisticsStatus()));
            response.put("summary", logisticsQuerySummary(order));
            return response;
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "查询电商物流失败，回退到mock响应 {}",
                    LogFields.chat(request.traceId(), request.sessionId(), request.userId()),
                    exception);
            Map<String, Object> response = mockLogisticsQueryResponse(skill, request, status);
            response.put("fallbackReason", "LOGISTICS_DOMAIN_QUERY_FAILED");
            return response;
        }
    }

    private Map<String, Object> buildOrderCancelResponse(
            SkillDefinitionView skill,
            SkillExecuteRequest request,
            String status) {
        if (!"SUCCEEDED".equals(status)) {
            return mockOrderCancelResponse(skill, request, status);
        }

        try {
            Map<String, Object> response = baseSkillResponse(skill, status);
            response.put("mock", false);
            response.put("source", "ecom_order");

            if (!hasExplicitOrderReference(request)) {
                response.put("found", false);
                response.put("cancelled", false);
                response.put("summary", "取消订单需要明确订单号，请补充订单号后再确认。");
                return response;
            }

            OrderSnapshot order = findOrderForQuery(request);
            response.put("found", order != null);
            if (order == null) {
                response.put("cancelled", false);
                response.put("summary", "暂未查询到可取消的订单，请补充订单号或联系人工客服。");
                return response;
            }

            response.put("orderId", order.orderId());
            response.put("orderNo", order.orderNo());
            response.put("previousOrderStatus", order.orderStatus());
            response.put("previousPayStatus", order.payStatus());
            response.put("logisticsStatus", order.logisticsStatus());
            if (!canApplyOrderCancel(order)) {
                response.put("cancelled", false);
                response.put("requiresHuman", true);
                response.put("summary", "当前订单状态不支持自动取消，请转人工处理。");
                return response;
            }

            applyOrderCancel(order);
            response.put("cancelled", true);
            response.put("refundHandled", false);
            response.put("orderStatus", "CANCELLED");
            response.put("summary", orderCancelSummary(order));
            return response;
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "取消电商订单失败，回退到mock响应 {}",
                    LogFields.chat(request.traceId(), request.sessionId(), request.userId()),
                    exception);
            Map<String, Object> response = mockOrderCancelResponse(skill, request, status);
            response.put("fallbackReason", "ORDER_CANCEL_FAILED");
            return response;
        }
    }

    private boolean canApplyAddressModify(OrderSnapshot order) {
        return order.canModifyAddress()
                && List.of("CREATED", "PAID", "PACKING").contains(order.orderStatus())
                && List.of("NONE", "WAITING_SHIP").contains(order.logisticsStatus());
    }

    private boolean canApplyOrderCancel(OrderSnapshot order) {
        return List.of("CREATED", "PAID", "PACKING").contains(order.orderStatus())
                && List.of("NONE", "WAITING_SHIP").contains(order.logisticsStatus());
    }

    private boolean hasExplicitOrderReference(SkillExecuteRequest request) {
        return hasText(firstText(request.parameters(), "order_id", "orderId", "order_no", "orderNo"));
    }

    private void applyAddressModify(
            OrderSnapshot order,
            AddressPayload address,
            String addressId,
            String messageId) {
        jdbcTemplate.update(
                """
                UPDATE ecom_order
                SET consignee_name = ?,
                    consignee_phone = ?,
                    province = ?,
                    city = ?,
                    district = ?,
                    address_detail = ?
                WHERE order_id = ?
                """,
                address.consigneeName(),
                address.consigneePhone(),
                address.province(),
                address.city(),
                address.district(),
                address.addressDetail(),
                order.orderId());
        jdbcTemplate.update(
                "UPDATE ecom_order_address SET is_current = 0 WHERE order_id = ? AND is_current = 1",
                order.orderId());
        jdbcTemplate.update(
                """
                INSERT INTO ecom_order_address (
                    address_id, order_id, user_id, consignee_name, consignee_phone,
                    province, city, district, address_detail, postal_code, is_current,
                    source, source_message_id, change_reason, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'AGENT_MODIFY', ?, ?, 'skill-engine')
                """,
                addressId,
                order.orderId(),
                order.userId(),
                address.consigneeName(),
                address.consigneePhone(),
                address.province(),
                address.city(),
                address.district(),
                address.addressDetail(),
                address.postalCode(),
                hasText(messageId) ? messageId : null,
                textOr(address.changeReason(), "USER_CONFIRMED_ADDRESS_MODIFY"));
    }

    private void applyOrderCancel(OrderSnapshot order) {
        // 当前只取消本地订单影子状态，不触发真实支付退款；退款仍走人工审核或后续支付流程。
        jdbcTemplate.update(
                """
                UPDATE ecom_order
                SET order_status = 'CANCELLED',
                    pay_status = CASE WHEN pay_status = 'UNPAID' THEN 'CLOSED' ELSE pay_status END,
                    logistics_status = CASE WHEN logistics_status IN ('NONE', 'WAITING_SHIP') THEN 'NONE' ELSE logistics_status END,
                    can_modify_address = 0
                WHERE order_id = ?
                """,
                order.orderId());
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

    private String logisticsQuerySummary(OrderSnapshot order) {
        return "已查询到订单 " + order.orderNo()
                + " 的物流信息：承运商顺丰速运，运单号 " + trackingNo(order)
                + "，当前状态 " + order.logisticsStatus()
                + "，最新节点：" + logisticsNode(order.logisticsStatus())
                + "，预计送达：" + estimatedDeliveryTime(order.logisticsStatus()) + "。";
    }

    private String orderCancelSummary(OrderSnapshot order) {
        return "已取消订单 " + order.orderNo() + "。如该订单已支付，退款仍需按售后或支付流程处理。";
    }

    private String trackingNo(OrderSnapshot order) {
        String normalized = order.orderNo() == null ? order.orderId() : order.orderNo().replaceAll("[^A-Za-z0-9]", "");
        return "SF" + normalized;
    }

    private String logisticsNode(String logisticsStatus) {
        return switch (textOr(logisticsStatus, "").toUpperCase()) {
            case "NONE" -> "订单尚未进入物流履约";
            case "WAITING_SHIP" -> "商家正在备货，等待仓库出库";
            case "IN_TRANSIT" -> "包裹已离开发货仓，正在运输途中";
            case "DELIVERING" -> "快递员正在派送";
            case "SIGNED" -> "包裹已签收";
            default -> "物流节点同步中";
        };
    }

    private String estimatedDeliveryTime(String logisticsStatus) {
        return switch (textOr(logisticsStatus, "").toUpperCase()) {
            case "NONE", "WAITING_SHIP" -> "发货后 2-4 天";
            case "IN_TRANSIT" -> "1-2 天内";
            case "DELIVERING" -> "今天";
            case "SIGNED" -> "已送达";
            default -> "暂无法预估";
        };
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

    private Map<String, Object> mockLogisticsQueryResponse(
            SkillDefinitionView skill,
            SkillExecuteRequest request,
            String status) {
        Map<String, Object> response = baseSkillResponse(skill, status);
        response.put("mock", true);
        response.put("orderNo", firstText(request.parameters(), "order_no", "orderNo"));
        response.put("carrier", "顺丰速运");
        response.put("trackingNo", "SFMOCK1002001");
        response.put("logisticsStatus", "IN_TRANSIT");
        response.put("latestNode", "包裹已离开发货仓，正在运输途中");
        response.put("estimatedDeliveryTime", "1-2 天内");
        response.put("summary", "已为你查询到物流信息：包裹正在运输途中，预计 1-2 天内送达。");
        return response;
    }

    private Map<String, Object> mockModifyAddressResponse(
            SkillDefinitionView skill,
            SkillExecuteRequest request,
            String status) {
        Map<String, Object> response = baseSkillResponse(skill, status);
        response.put("mock", true);
        response.put("orderId", textFrom(request.parameters(), "order_id", "orderId", "mock-order"));
        response.put("orderNo", firstText(request.parameters(), "order_no", "orderNo"));
        response.put("modifyRequestId", "addr_" + UUID.randomUUID());
        response.put("summary", "已记录改地址请求，真实电商订单 API 后续接入。");
        return response;
    }

    private Map<String, Object> mockOrderCancelResponse(
            SkillDefinitionView skill,
            SkillExecuteRequest request,
            String status) {
        Map<String, Object> response = baseSkillResponse(skill, status);
        response.put("mock", true);
        response.put("orderNo", firstText(request.parameters(), "order_no", "orderNo"));
        response.put("cancelled", false);
        response.put("refundHandled", false);
        response.put("summary", "已记录取消订单请求，真实电商订单 API 后续接入。");
        return response;
    }

    private AddressPayload readAddressPayload(Map<String, Object> values) {
        return new AddressPayload(
                firstAddressText(values, "consignee_name", "consigneeName", "name"),
                firstAddressText(values, "consignee_phone", "consigneePhone", "phone"),
                firstAddressText(values, "province"),
                firstAddressText(values, "city"),
                firstAddressText(values, "district"),
                firstAddressText(values, "address_detail", "addressDetail", "detail"),
                firstAddressText(values, "postal_code", "postalCode"),
                firstAddressText(values, "change_reason", "changeReason", "reason"));
    }

    private List<String> missingAddressFields(AddressPayload address) {
        List<String> missingFields = new ArrayList<>();
        if (!hasText(address.consigneeName())) {
            missingFields.add("consigneeName");
        }
        if (!hasText(address.consigneePhone())) {
            missingFields.add("consigneePhone");
        }
        if (!hasText(address.province())) {
            missingFields.add("province");
        }
        if (!hasText(address.city())) {
            missingFields.add("city");
        }
        if (!hasText(address.district())) {
            missingFields.add("district");
        }
        if (!hasText(address.addressDetail())) {
            missingFields.add("addressDetail");
        }
        return missingFields;
    }

    private Map<String, Object> addressResponse(AddressPayload address) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("consigneeName", address.consigneeName());
        response.put("consigneePhone", address.consigneePhone());
        response.put("province", address.province());
        response.put("city", address.city());
        response.put("district", address.district());
        response.put("addressDetail", address.addressDetail());
        response.put("postalCode", address.postalCode());
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
            case "SUCCEEDED" -> successMessage(skill.intent());
            case "REVIEW_REQUIRED" -> "该请求需要人工审核，Agent 不直接执行敏感操作。";
            case "PENDING" -> "该请求需要用户确认后再执行。";
            case "CANCELLED" -> "该技能本次未执行，路由决策为 " + routeDecision.name() + "。";
            default -> "技能执行状态：" + status;
        };
    }

    private String successMessage(String intent) {
        return switch (intent) {
            case "order.query" -> "已完成订单查询。";
            case "logistics.query" -> "已完成物流查询。";
            case "order.cancel" -> "已完成订单取消。";
            default -> "已完成技能执行，真实业务 API 后续接入。";
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
                    "Skill Engine执行日志已落库 {} executionId={} skillId={} status={}",
                    LogFields.chat(result.traceId(), result.sessionId(), request.userId()),
                    LogFields.value(result.executionId()),
                    LogFields.value(result.skillId()),
                    LogFields.value(result.status()));
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "Skill Engine执行日志落库失败 {} executionId={} skillId={} status={}",
                    LogFields.chat(result.traceId(), result.sessionId(), request.userId()),
                    LogFields.value(result.executionId()),
                    LogFields.value(result.skillId()),
                    LogFields.value(result.status()),
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

    private String firstAddressText(Map<String, Object> values, String... keys) {
        if (values == null) {
            return "";
        }
        Object nested = values.get("newAddress");
        if (!(nested instanceof Map<?, ?>)) {
            nested = values.get("address");
        }
        if (nested instanceof Map<?, ?> nestedValues) {
            for (String key : keys) {
                Object value = nestedValues.get(key);
                if (value != null && !value.toString().isBlank()) {
                    return value.toString();
                }
            }
        }
        return firstText(values, keys);
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

    private record AddressPayload(
            String consigneeName,
            String consigneePhone,
            String province,
            String city,
            String district,
            String addressDetail,
            String postalCode,
            String changeReason) {}
}
