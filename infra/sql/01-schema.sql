-- SmartCS Agent phase-1 core schema
-- MySQL 8.x / InnoDB / utf8mb4

CREATE DATABASE IF NOT EXISTS `smartcs_agent`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `smartcs_agent`;

-- ============================================================
-- Conversation session
-- ============================================================
CREATE TABLE IF NOT EXISTS `cs_session` (
    `session_id`       VARCHAR(64)   NOT NULL COMMENT '会话ID',
    `trace_id`         VARCHAR(64)   NOT NULL COMMENT '当前链路追踪ID',
    `user_id`          VARCHAR(64)   NOT NULL COMMENT '用户ID',
    `channel`          VARCHAR(32)   NOT NULL DEFAULT 'h5' COMMENT '接入渠道: h5/app_h5/web/mini_program',
    `status`           ENUM('ACTIVE', 'HUMAN_TAKEOVER', 'TIMEOUT', 'CLOSED') NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态',
    `dialog_state`     ENUM('INIT', 'INTENT_RECOGNIZED', 'SLOT_FILLING', 'READY_TO_EXECUTE', 'WAITING_CONFIRM', 'EXECUTING', 'EXECUTED', 'COMPLETED', 'HUMAN_REVIEW', 'HUMAN_TAKEOVER', 'TIMEOUT', 'CLOSED') NOT NULL DEFAULT 'INIT' COMMENT '对话状态，参考 DialogState 枚举',
    `current_intent`   VARCHAR(64)   DEFAULT NULL COMMENT '当前意图编码',
    `slots`            JSON          DEFAULT NULL COMMENT '当前槽位快照',
    `context_snapshot` JSON          DEFAULT NULL COMMENT '会话上下文快照',
    `last_message_at`  DATETIME(3)   DEFAULT NULL COMMENT '最近消息时间',
    `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `closed_at`        DATETIME(3)   DEFAULT NULL COMMENT '关闭时间',
    PRIMARY KEY (`session_id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_trace` (`trace_id`),
    KEY `idx_channel_status` (`channel`, `status`),
    KEY `idx_last_message` (`last_message_at`),
    CONSTRAINT `chk_cs_session_status`
        CHECK (`status` IN ('ACTIVE', 'HUMAN_TAKEOVER', 'TIMEOUT', 'CLOSED')),
    CONSTRAINT `chk_cs_session_dialog_state`
        CHECK (`dialog_state` IN ('INIT', 'INTENT_RECOGNIZED', 'SLOT_FILLING', 'READY_TO_EXECUTE', 'WAITING_CONFIRM', 'EXECUTING', 'EXECUTED', 'COMPLETED', 'HUMAN_REVIEW', 'HUMAN_TAKEOVER', 'TIMEOUT', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客服会话表';

-- ============================================================
-- Conversation message
-- ============================================================
CREATE TABLE IF NOT EXISTS `cs_message` (
    `message_id`       VARCHAR(64)   NOT NULL COMMENT '消息ID',
    `trace_id`         VARCHAR(64)   NOT NULL COMMENT '链路追踪ID',
    `session_id`       VARCHAR(64)   NOT NULL COMMENT '会话ID',
    `user_id`          VARCHAR(64)   NOT NULL COMMENT '用户ID',
    `role`             ENUM('USER', 'AGENT', 'HUMAN_AGENT', 'SYSTEM') NOT NULL COMMENT '消息角色',
    `message_type`     ENUM('TEXT', 'IMAGE', 'FILE', 'CARD', 'ACTION', 'SYSTEM') NOT NULL DEFAULT 'TEXT' COMMENT '消息类型',
    `content`          TEXT          DEFAULT NULL COMMENT '消息文本内容',
    `attachments`      JSON          DEFAULT NULL COMMENT '附件元数据',
    `quick_actions`    JSON          DEFAULT NULL COMMENT '快捷操作',
    `intent`           VARCHAR(64)   DEFAULT NULL COMMENT '关联意图编码',
    `risk_level`       ENUM('L0', 'L1', 'L2', 'L3') DEFAULT NULL COMMENT '风险等级',
    `route_decision`   ENUM('AUTO_REPLY', 'AUTO_EXECUTE', 'CONFIRM_BEFORE_EXECUTE', 'HUMAN_REVIEW', 'HUMAN_TAKEOVER', 'REJECT') DEFAULT NULL COMMENT '路由决策',
    `metadata`         JSON          DEFAULT NULL COMMENT '扩展元数据',
    `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`message_id`),
    KEY `idx_session_created` (`session_id`, `created_at`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_trace` (`trace_id`),
    KEY `idx_intent` (`intent`),
    CONSTRAINT `fk_cs_message_session`
        FOREIGN KEY (`session_id`) REFERENCES `cs_session` (`session_id`),
    CONSTRAINT `chk_cs_message_role`
        CHECK (`role` IN ('USER', 'AGENT', 'HUMAN_AGENT', 'SYSTEM')),
    CONSTRAINT `chk_cs_message_type`
        CHECK (`message_type` IN ('TEXT', 'IMAGE', 'FILE', 'CARD', 'ACTION', 'SYSTEM')),
    CONSTRAINT `chk_cs_message_risk_level`
        CHECK (`risk_level` IS NULL OR `risk_level` IN ('L0', 'L1', 'L2', 'L3')),
    CONSTRAINT `chk_cs_message_route_decision`
        CHECK (`route_decision` IS NULL OR `route_decision` IN ('AUTO_REPLY', 'AUTO_EXECUTE', 'CONFIRM_BEFORE_EXECUTE', 'HUMAN_REVIEW', 'HUMAN_TAKEOVER', 'REJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客服消息表';

-- ============================================================
-- Intent recognition record
-- ============================================================
CREATE TABLE IF NOT EXISTS `intent_record` (
    `record_id`        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '识别记录ID',
    `trace_id`         VARCHAR(64)   NOT NULL COMMENT '链路追踪ID',
    `session_id`       VARCHAR(64)   NOT NULL COMMENT '会话ID',
    `message_id`       VARCHAR(64)   DEFAULT NULL COMMENT '来源消息ID',
    `intent`           VARCHAR(64)   NOT NULL COMMENT '识别出的意图编码',
    `confidence`       DECIMAL(5,4)  NOT NULL COMMENT '置信度，范围 0-1',
    `entities`         JSON          DEFAULT NULL COMMENT '实体抽取结果',
    `reasoning`        VARCHAR(1024) DEFAULT NULL COMMENT '简要识别依据',
    `model_name`       VARCHAR(64)   DEFAULT NULL COMMENT '模型名称',
    `model_version`    VARCHAR(64)   DEFAULT NULL COMMENT '模型版本',
    `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`record_id`),
    KEY `idx_session_created` (`session_id`, `created_at`),
    KEY `idx_message` (`message_id`),
    KEY `idx_intent_confidence` (`intent`, `confidence`),
    KEY `idx_trace` (`trace_id`),
    CONSTRAINT `fk_intent_record_session`
        FOREIGN KEY (`session_id`) REFERENCES `cs_session` (`session_id`),
    CONSTRAINT `fk_intent_record_message`
        FOREIGN KEY (`message_id`) REFERENCES `cs_message` (`message_id`),
    CONSTRAINT `chk_intent_confidence`
        CHECK (`confidence` >= 0 AND `confidence` <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='意图识别记录表';

-- ============================================================
-- Risk decision record
-- ============================================================
CREATE TABLE IF NOT EXISTS `risk_decision` (
    `decision_id`      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '风控决策ID',
    `trace_id`         VARCHAR(64)   NOT NULL COMMENT '链路追踪ID',
    `session_id`       VARCHAR(64)   NOT NULL COMMENT '会话ID',
    `message_id`       VARCHAR(64)   DEFAULT NULL COMMENT '来源消息ID',
    `intent`           VARCHAR(64)   NOT NULL COMMENT '意图编码',
    `risk_level`       ENUM('L0', 'L1', 'L2', 'L3') NOT NULL COMMENT '风险等级',
    `route_decision`   ENUM('AUTO_REPLY', 'AUTO_EXECUTE', 'CONFIRM_BEFORE_EXECUTE', 'HUMAN_REVIEW', 'HUMAN_TAKEOVER', 'REJECT') NOT NULL COMMENT '路由决策',
    `reason_code`      VARCHAR(64)   DEFAULT NULL COMMENT '决策原因编码',
    `reason`           VARCHAR(1024) DEFAULT NULL COMMENT '决策原因说明',
    `matched_rule_ids` JSON          DEFAULT NULL COMMENT '命中的规则ID列表',
    `details`          JSON          DEFAULT NULL COMMENT '决策详情',
    `decided_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`decision_id`),
    KEY `idx_session_decided` (`session_id`, `decided_at`),
    KEY `idx_intent_risk` (`intent`, `risk_level`),
    KEY `idx_route_decision` (`route_decision`),
    KEY `idx_trace` (`trace_id`),
    CONSTRAINT `fk_risk_decision_session`
        FOREIGN KEY (`session_id`) REFERENCES `cs_session` (`session_id`),
    CONSTRAINT `fk_risk_decision_message`
        FOREIGN KEY (`message_id`) REFERENCES `cs_message` (`message_id`),
    CONSTRAINT `chk_risk_level`
        CHECK (`risk_level` IN ('L0', 'L1', 'L2', 'L3')),
    CONSTRAINT `chk_route_decision`
        CHECK (`route_decision` IN ('AUTO_REPLY', 'AUTO_EXECUTE', 'CONFIRM_BEFORE_EXECUTE', 'HUMAN_REVIEW', 'HUMAN_TAKEOVER', 'REJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控决策记录表';

-- ============================================================
-- Human review work order
-- ============================================================
CREATE TABLE IF NOT EXISTS `work_order` (
    `ticket_id`        VARCHAR(64)   NOT NULL COMMENT '工单ID',
    `trace_id`         VARCHAR(64)   NOT NULL COMMENT '链路追踪ID',
    `session_id`       VARCHAR(64)   NOT NULL COMMENT '关联会话ID',
    `user_id`          VARCHAR(64)   NOT NULL COMMENT '用户ID',
    `intent`           VARCHAR(64)   NOT NULL COMMENT '意图编码',
    `risk_level`       ENUM('L0', 'L1', 'L2', 'L3') NOT NULL COMMENT '风险等级',
    `route_decision`   ENUM('AUTO_REPLY', 'AUTO_EXECUTE', 'CONFIRM_BEFORE_EXECUTE', 'HUMAN_REVIEW', 'HUMAN_TAKEOVER', 'REJECT') NOT NULL COMMENT '触发工单的路由决策',
    `status`           ENUM('PENDING', 'ASSIGNED', 'PROCESSING', 'APPROVED', 'REJECTED', 'RESOLVED', 'CLOSED', 'ESCALATED') NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    `priority`         ENUM('LOW', 'NORMAL', 'HIGH', 'URGENT') NOT NULL DEFAULT 'NORMAL' COMMENT '优先级',
    `assigned_agent`   VARCHAR(64)   DEFAULT NULL COMMENT '分配坐席ID',
    `reason`           VARCHAR(1024) DEFAULT NULL COMMENT '转人工或审核原因',
    `context_snapshot` JSON          DEFAULT NULL COMMENT '进入人工链路时的上下文快照',
    `resolution`       JSON          DEFAULT NULL COMMENT '处理结果',
    `sla_deadline`     DATETIME(3)   DEFAULT NULL COMMENT 'SLA 截止时间',
    `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `resolved_at`      DATETIME(3)   DEFAULT NULL COMMENT '解决时间',
    PRIMARY KEY (`ticket_id`),
    KEY `idx_status_priority` (`status`, `priority`),
    KEY `idx_assigned_status` (`assigned_agent`, `status`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_session` (`session_id`),
    KEY `idx_sla` (`sla_deadline`),
    KEY `idx_trace` (`trace_id`),
    KEY `idx_updated_ticket` (`updated_at`, `ticket_id`),
    CONSTRAINT `fk_work_order_session`
        FOREIGN KEY (`session_id`) REFERENCES `cs_session` (`session_id`),
    CONSTRAINT `chk_work_order_risk_level`
        CHECK (`risk_level` IN ('L0', 'L1', 'L2', 'L3')),
    CONSTRAINT `chk_work_order_route_decision`
        CHECK (`route_decision` IN ('AUTO_REPLY', 'AUTO_EXECUTE', 'CONFIRM_BEFORE_EXECUTE', 'HUMAN_REVIEW', 'HUMAN_TAKEOVER', 'REJECT')),
    CONSTRAINT `chk_work_order_status`
        CHECK (`status` IN ('PENDING', 'ASSIGNED', 'PROCESSING', 'APPROVED', 'REJECTED', 'RESOLVED', 'CLOSED', 'ESCALATED')),
    CONSTRAINT `chk_work_order_priority`
        CHECK (`priority` IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人工审核工单表';

-- ============================================================
-- Human work order action
-- ============================================================
CREATE TABLE IF NOT EXISTS `work_order_action` (
    `action_id`        VARCHAR(64)   NOT NULL COMMENT '工单动作ID',
    `ticket_id`        VARCHAR(64)   NOT NULL COMMENT '工单ID',
    `trace_id`         VARCHAR(64)   NOT NULL COMMENT '链路追踪ID',
    `operator_id`      VARCHAR(64)   NOT NULL COMMENT '操作人ID',
    `action_type`      ENUM('CREATE', 'ASSIGN', 'APPROVE', 'REJECT', 'MODIFY_AND_APPROVE', 'TAKEOVER', 'ESCALATE', 'CLOSE', 'INTERNAL_NOTE') NOT NULL COMMENT '动作',
    `comment`          VARCHAR(1024) DEFAULT NULL COMMENT '操作备注',
    `action_data`      JSON          DEFAULT NULL COMMENT '操作数据',
    `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`action_id`),
    KEY `idx_ticket_created` (`ticket_id`, `created_at`),
    KEY `idx_operator_created` (`operator_id`, `created_at`),
    KEY `idx_action_type` (`action_type`),
    KEY `idx_trace` (`trace_id`),
    CONSTRAINT `fk_work_order_action_ticket`
        FOREIGN KEY (`ticket_id`) REFERENCES `work_order` (`ticket_id`),
    CONSTRAINT `chk_work_order_action_type`
        CHECK (`action_type` IN ('CREATE', 'ASSIGN', 'APPROVE', 'REJECT', 'MODIFY_AND_APPROVE', 'TAKEOVER', 'ESCALATE', 'CLOSE', 'INTERNAL_NOTE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单操作记录表';

-- ============================================================
-- Audit log
-- ============================================================
CREATE TABLE IF NOT EXISTS `audit_log` (
    `event_id`         VARCHAR(64)   NOT NULL COMMENT '审计事件ID',
    `trace_id`         VARCHAR(64)   NOT NULL COMMENT '链路追踪ID',
    `session_id`       VARCHAR(64)   DEFAULT NULL COMMENT '会话ID',
    `ticket_id`        VARCHAR(64)   DEFAULT NULL COMMENT '工单ID',
    `user_id`          VARCHAR(64)   DEFAULT NULL COMMENT '用户ID，生产写入前应脱敏',
    `operator_id`      VARCHAR(64)   DEFAULT NULL COMMENT '操作人ID',
    `event_type`       VARCHAR(64)   NOT NULL COMMENT '事件类型',
    `event_data`       JSON          DEFAULT NULL COMMENT '事件数据，生产写入前应脱敏',
    `occurred_at`      DATETIME(3)   NOT NULL COMMENT '事件发生时间',
    `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`event_id`),
    KEY `idx_trace` (`trace_id`),
    KEY `idx_session_occurred` (`session_id`, `occurred_at`),
    KEY `idx_ticket_occurred` (`ticket_id`, `occurred_at`),
    KEY `idx_user_occurred` (`user_id`, `occurred_at`),
    KEY `idx_event_type_occurred` (`event_type`, `occurred_at`),
    KEY `idx_occurred_event` (`occurred_at`, `event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志表';
