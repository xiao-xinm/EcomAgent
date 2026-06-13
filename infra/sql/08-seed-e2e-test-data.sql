-- SmartCS Agent end-to-end acceptance seed data
-- MySQL 8.x / InnoDB / utf8mb4
--
-- This script is idempotent. It resets only records whose ids start with
-- the fixed e2e_* identifiers below, and does not touch real business data.

USE `smartcs_agent`;

-- Keep the skill FK stable even if 07-seed-skill-registry.sql was not run.
INSERT IGNORE INTO `skill_registry` (
    `skill_id`,
    `skill_name`,
    `intent`,
    `base_risk_level`,
    `execution_type`,
    `enabled`,
    `status`,
    `description`,
    `pre_conditions`,
    `retry_policy`,
    `timeout_ms`,
    `owner`,
    `version`
) VALUES (
    'order_query',
    '订单查询',
    'order.query',
    'L0',
    'SYNC',
    1,
    'ACTIVE',
    'E2E acceptance fallback skill for order query.',
    JSON_OBJECT('requires_authenticated_user', true),
    JSON_OBJECT('max_retries', 1, 'backoff_ms', 500),
    10000,
    'smartcs',
    '1.0.0'
);

SET @e2e_order_session = 'e2e_s_order_query';
SET @e2e_refund_session = 'e2e_s_refund_review';
SET @e2e_takeover_session = 'e2e_s_takeover';

SET @e2e_refund_ticket = 'e2e_wo_refund_review';
SET @e2e_takeover_ticket = 'e2e_wo_takeover';

-- Reset previous e2e records in FK-safe order.
DELETE FROM `approval_action`
WHERE `ticket_id` IN (@e2e_refund_ticket, @e2e_takeover_ticket);

DELETE FROM `work_order_action`
WHERE `ticket_id` IN (@e2e_refund_ticket, @e2e_takeover_ticket);

DELETE FROM `audit_log`
WHERE `session_id` IN (@e2e_order_session, @e2e_refund_session, @e2e_takeover_session)
   OR `ticket_id` IN (@e2e_refund_ticket, @e2e_takeover_ticket);

DELETE FROM `skill_execution_log`
WHERE `session_id` IN (@e2e_order_session, @e2e_refund_session, @e2e_takeover_session);

DELETE FROM `human_takeover`
WHERE `ticket_id` IN (@e2e_refund_ticket, @e2e_takeover_ticket)
   OR `session_id` IN (@e2e_order_session, @e2e_refund_session, @e2e_takeover_session);

DELETE FROM `approval_task`
WHERE `ticket_id` IN (@e2e_refund_ticket, @e2e_takeover_ticket)
   OR `session_id` IN (@e2e_order_session, @e2e_refund_session, @e2e_takeover_session);

DELETE FROM `work_order`
WHERE `ticket_id` IN (@e2e_refund_ticket, @e2e_takeover_ticket)
   OR `session_id` IN (@e2e_order_session, @e2e_refund_session, @e2e_takeover_session);

DELETE FROM `risk_decision`
WHERE `session_id` IN (@e2e_order_session, @e2e_refund_session, @e2e_takeover_session);

DELETE FROM `intent_record`
WHERE `session_id` IN (@e2e_order_session, @e2e_refund_session, @e2e_takeover_session);

DELETE FROM `cs_message`
WHERE `session_id` IN (@e2e_order_session, @e2e_refund_session, @e2e_takeover_session);

DELETE FROM `cs_session`
WHERE `session_id` IN (@e2e_order_session, @e2e_refund_session, @e2e_takeover_session);

-- Scenario 1: low-risk order query, already handled by Agent + Skill Engine.
INSERT INTO `cs_session` (
    `session_id`,
    `trace_id`,
    `user_id`,
    `channel`,
    `status`,
    `dialog_state`,
    `current_intent`,
    `slots`,
    `context_snapshot`,
    `last_message_at`,
    `created_at`
) VALUES (
    @e2e_order_session,
    'e2e_trace_order_query',
    'u_e2e_1001',
    'h5',
    'ACTIVE',
    'COMPLETED',
    'order.query',
    JSON_OBJECT('order_id', 'E2E-ORDER-1001'),
    JSON_OBJECT('scenario', 'AUTO_REPLY order query'),
    CURRENT_TIMESTAMP(3),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 30 MINUTE)
);

INSERT INTO `cs_message` (
    `message_id`,
    `trace_id`,
    `session_id`,
    `user_id`,
    `role`,
    `message_type`,
    `content`,
    `intent`,
    `risk_level`,
    `route_decision`,
    `metadata`,
    `created_at`
) VALUES (
    'e2e_m_order_user',
    'e2e_trace_order_query',
    @e2e_order_session,
    'u_e2e_1001',
    'USER',
    'TEXT',
    '我的订单',
    'order.query',
    'L0',
    'AUTO_REPLY',
    JSON_OBJECT('seed', true),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 29 MINUTE)
), (
    'e2e_m_order_agent',
    'e2e_trace_order_query',
    @e2e_order_session,
    'u_e2e_1001',
    'AGENT',
    'TEXT',
    '已为你查询到最近订单，当前订单已发货，物流运输中。',
    'order.query',
    'L0',
    'AUTO_REPLY',
    JSON_OBJECT('seed', true, 'skillExecutionId', 'e2e_se_order_query', 'skillExecutionStatus', 'SUCCEEDED'),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 28 MINUTE)
);

INSERT INTO `skill_execution_log` (
    `execution_id`,
    `trace_id`,
    `session_id`,
    `message_id`,
    `skill_id`,
    `intent`,
    `user_id`,
    `risk_level`,
    `route_decision`,
    `status`,
    `request_snapshot`,
    `step_results`,
    `response_snapshot`,
    `started_at`,
    `finished_at`,
    `created_at`
) VALUES (
    'e2e_se_order_query',
    'e2e_trace_order_query',
    @e2e_order_session,
    'e2e_m_order_user',
    'order_query',
    'order.query',
    'u_e2e_1001',
    'L0',
    'AUTO_REPLY',
    'SUCCEEDED',
    JSON_OBJECT('seed', true, 'content', '我的订单'),
    JSON_ARRAY(JSON_OBJECT('stepNo', 1, 'status', 'SUCCEEDED')),
    JSON_OBJECT('mock', true, 'orderStatus', 'SHIPPED', 'summary', '订单已发货，物流运输中'),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 28 MINUTE),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 28 MINUTE),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 28 MINUTE)
);

-- Scenario 2: refund request waiting for human review.
INSERT INTO `cs_session` (
    `session_id`,
    `trace_id`,
    `user_id`,
    `channel`,
    `status`,
    `dialog_state`,
    `current_intent`,
    `slots`,
    `context_snapshot`,
    `last_message_at`,
    `created_at`
) VALUES (
    @e2e_refund_session,
    'e2e_trace_refund_review',
    'u_e2e_1002',
    'h5',
    'ACTIVE',
    'HUMAN_REVIEW',
    'refund.apply',
    JSON_OBJECT('order_id', 'E2E-ORDER-2001', 'refund_reason', '不想要了'),
    JSON_OBJECT('scenario', 'HUMAN_REVIEW refund'),
    CURRENT_TIMESTAMP(3),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 20 MINUTE)
);

INSERT INTO `cs_message` (
    `message_id`,
    `trace_id`,
    `session_id`,
    `user_id`,
    `role`,
    `message_type`,
    `content`,
    `intent`,
    `risk_level`,
    `route_decision`,
    `metadata`,
    `created_at`
) VALUES (
    'e2e_m_refund_user',
    'e2e_trace_refund_review',
    @e2e_refund_session,
    'u_e2e_1002',
    'USER',
    'TEXT',
    '我要退款',
    'refund.apply',
    'L3',
    'HUMAN_REVIEW',
    JSON_OBJECT('seed', true),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 19 MINUTE)
), (
    'e2e_m_refund_agent',
    'e2e_trace_refund_review',
    @e2e_refund_session,
    'u_e2e_1002',
    'AGENT',
    'TEXT',
    '这个请求需要人工审核，我已创建工单：e2e_wo_refund_review。',
    'refund.apply',
    'L3',
    'HUMAN_REVIEW',
    JSON_OBJECT('seed', true, 'ticketId', @e2e_refund_ticket),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 18 MINUTE)
);

INSERT INTO `work_order` (
    `ticket_id`,
    `trace_id`,
    `session_id`,
    `user_id`,
    `intent`,
    `risk_level`,
    `route_decision`,
    `status`,
    `priority`,
    `reason`,
    `context_snapshot`,
    `sla_deadline`,
    `created_at`
) VALUES (
    @e2e_refund_ticket,
    'e2e_trace_refund_review',
    @e2e_refund_session,
    'u_e2e_1002',
    'refund.apply',
    'L3',
    'HUMAN_REVIEW',
    'PENDING',
    'HIGH',
    '退款申请需要人工审核',
    JSON_OBJECT('seed', true, 'orderId', 'E2E-ORDER-2001', 'userMessageId', 'e2e_m_refund_user'),
    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 2 HOUR),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 18 MINUTE)
);

INSERT INTO `approval_task` (
    `approval_id`,
    `ticket_id`,
    `trace_id`,
    `session_id`,
    `user_id`,
    `intent`,
    `approval_type`,
    `risk_level`,
    `route_decision`,
    `status`,
    `priority`,
    `risk_reason`,
    `request_payload`,
    `context_snapshot`,
    `expire_at`,
    `created_at`
) VALUES (
    'e2e_ap_refund_review',
    @e2e_refund_ticket,
    'e2e_trace_refund_review',
    @e2e_refund_session,
    'u_e2e_1002',
    'refund.apply',
    'REFUND',
    'L3',
    'HUMAN_REVIEW',
    'PENDING',
    'HIGH',
    '退款申请需要人工审核',
    JSON_OBJECT('orderId', 'E2E-ORDER-2001', 'refundReason', '不想要了'),
    JSON_OBJECT('seed', true, 'ticketId', @e2e_refund_ticket),
    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 2 HOUR),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 18 MINUTE)
);

INSERT INTO `work_order_action` (
    `action_id`,
    `ticket_id`,
    `trace_id`,
    `operator_id`,
    `action_type`,
    `comment`,
    `action_data`,
    `created_at`
) VALUES (
    'e2e_wa_refund_create',
    @e2e_refund_ticket,
    'e2e_trace_refund_review',
    'system',
    'CREATE',
    'E2E seed created refund review work order.',
    JSON_OBJECT('seed', true),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 18 MINUTE)
);

INSERT INTO `audit_log` (
    `event_id`,
    `trace_id`,
    `session_id`,
    `ticket_id`,
    `user_id`,
    `operator_id`,
    `event_type`,
    `event_data`,
    `occurred_at`
) VALUES (
    'e2e_evt_refund_create',
    'e2e_trace_refund_review',
    @e2e_refund_session,
    @e2e_refund_ticket,
    'u_e2e_1002',
    'system',
    'WORK_ORDER_CREATED',
    JSON_OBJECT('seed', true, 'routeDecision', 'HUMAN_REVIEW'),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 18 MINUTE)
);

-- Scenario 3: human takeover waiting for an agent.
INSERT INTO `cs_session` (
    `session_id`,
    `trace_id`,
    `user_id`,
    `channel`,
    `status`,
    `dialog_state`,
    `current_intent`,
    `slots`,
    `context_snapshot`,
    `last_message_at`,
    `created_at`
) VALUES (
    @e2e_takeover_session,
    'e2e_trace_takeover',
    'u_e2e_1003',
    'h5',
    'HUMAN_TAKEOVER',
    'HUMAN_TAKEOVER',
    'unknown',
    JSON_OBJECT(),
    JSON_OBJECT('scenario', 'HUMAN_TAKEOVER manual support'),
    CURRENT_TIMESTAMP(3),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 10 MINUTE)
);

INSERT INTO `cs_message` (
    `message_id`,
    `trace_id`,
    `session_id`,
    `user_id`,
    `role`,
    `message_type`,
    `content`,
    `intent`,
    `risk_level`,
    `route_decision`,
    `metadata`,
    `created_at`
) VALUES (
    'e2e_m_takeover_user',
    'e2e_trace_takeover',
    @e2e_takeover_session,
    'u_e2e_1003',
    'USER',
    'TEXT',
    '我要找人工客服',
    'unknown',
    'L3',
    'HUMAN_TAKEOVER',
    JSON_OBJECT('seed', true),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 9 MINUTE)
), (
    'e2e_m_takeover_agent',
    'e2e_trace_takeover',
    @e2e_takeover_session,
    'u_e2e_1003',
    'AGENT',
    'TEXT',
    '我已为你转人工处理，工单：e2e_wo_takeover，请稍等。',
    'unknown',
    'L3',
    'HUMAN_TAKEOVER',
    JSON_OBJECT('seed', true, 'ticketId', @e2e_takeover_ticket),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 8 MINUTE)
);

INSERT INTO `work_order` (
    `ticket_id`,
    `trace_id`,
    `session_id`,
    `user_id`,
    `intent`,
    `risk_level`,
    `route_decision`,
    `status`,
    `priority`,
    `reason`,
    `context_snapshot`,
    `sla_deadline`,
    `created_at`
) VALUES (
    @e2e_takeover_ticket,
    'e2e_trace_takeover',
    @e2e_takeover_session,
    'u_e2e_1003',
    'unknown',
    'L3',
    'HUMAN_TAKEOVER',
    'PENDING',
    'NORMAL',
    '用户请求人工客服',
    JSON_OBJECT('seed', true, 'userMessageId', 'e2e_m_takeover_user'),
    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 HOUR),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 8 MINUTE)
);

INSERT INTO `human_takeover` (
    `takeover_id`,
    `ticket_id`,
    `trace_id`,
    `session_id`,
    `user_id`,
    `trigger_source`,
    `status`,
    `priority`,
    `reason`,
    `context_snapshot`,
    `created_at`
) VALUES (
    'e2e_ht_takeover',
    @e2e_takeover_ticket,
    'e2e_trace_takeover',
    @e2e_takeover_session,
    'u_e2e_1003',
    'USER_REQUEST',
    'REQUESTED',
    'NORMAL',
    '用户请求人工客服',
    JSON_OBJECT('seed', true, 'ticketId', @e2e_takeover_ticket),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 8 MINUTE)
);

INSERT INTO `work_order_action` (
    `action_id`,
    `ticket_id`,
    `trace_id`,
    `operator_id`,
    `action_type`,
    `comment`,
    `action_data`,
    `created_at`
) VALUES (
    'e2e_wa_takeover_create',
    @e2e_takeover_ticket,
    'e2e_trace_takeover',
    'system',
    'CREATE',
    'E2E seed created human takeover work order.',
    JSON_OBJECT('seed', true),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 8 MINUTE)
);

INSERT INTO `audit_log` (
    `event_id`,
    `trace_id`,
    `session_id`,
    `ticket_id`,
    `user_id`,
    `operator_id`,
    `event_type`,
    `event_data`,
    `occurred_at`
) VALUES (
    'e2e_evt_takeover_create',
    'e2e_trace_takeover',
    @e2e_takeover_session,
    @e2e_takeover_ticket,
    'u_e2e_1003',
    'system',
    'WORK_ORDER_CREATED',
    JSON_OBJECT('seed', true, 'routeDecision', 'HUMAN_TAKEOVER'),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 8 MINUTE)
);
