-- SmartCS Agent phase-3 risk rule and human approval schema
-- MySQL 8.x / InnoDB / utf8mb4

USE `smartcs_agent`;

-- ============================================================
-- Risk rule
-- ============================================================
CREATE TABLE IF NOT EXISTS `risk_rule` (
    `rule_id`          VARCHAR(64)   NOT NULL COMMENT '风控规则ID',
    `rule_name`        VARCHAR(128)  NOT NULL COMMENT '规则名称',
    `intent_pattern`   VARCHAR(128)  DEFAULT NULL COMMENT '适用意图，支持精确值或前缀模式',
    `phase`            ENUM('NLU', 'SLOT_FILLING', 'PRE_EXECUTION', 'POST_EXECUTION', 'GLOBAL') NOT NULL DEFAULT 'PRE_EXECUTION' COMMENT '评估阶段',
    `condition_expr`   TEXT          NOT NULL COMMENT '条件表达式，后续由风控引擎解释',
    `target_risk_level` ENUM('L0', 'L1', 'L2', 'L3') NOT NULL COMMENT '命中后目标风险等级',
    `route_decision`   ENUM('AUTO_REPLY', 'AUTO_EXECUTE', 'CONFIRM_BEFORE_EXECUTE', 'HUMAN_REVIEW', 'HUMAN_TAKEOVER', 'REJECT') NOT NULL COMMENT '命中后路由决策',
    `action`           ENUM('KEEP', 'UPGRADE', 'REVIEW', 'TAKEOVER', 'REJECT', 'NOTIFY') NOT NULL DEFAULT 'UPGRADE' COMMENT '规则动作',
    `priority`         INT           NOT NULL DEFAULT 0 COMMENT '优先级，数值越大越优先',
    `enabled`          TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    `description`      VARCHAR(512)  DEFAULT NULL COMMENT '规则说明',
    `version`          VARCHAR(32)   NOT NULL DEFAULT '1.0.0' COMMENT '规则版本',
    `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`rule_id`),
    KEY `idx_intent_enabled_priority` (`intent_pattern`, `enabled`, `priority`),
    KEY `idx_phase_enabled_priority` (`phase`, `enabled`, `priority`),
    KEY `idx_route_decision` (`route_decision`),
    CONSTRAINT `chk_risk_rule_enabled`
        CHECK (`enabled` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控规则表';

-- ============================================================
-- Approval task
-- ============================================================
CREATE TABLE IF NOT EXISTS `approval_task` (
    `approval_id`      VARCHAR(64)   NOT NULL COMMENT '审批任务ID',
    `ticket_id`        VARCHAR(64)   NOT NULL COMMENT '关联工单ID',
    `trace_id`         VARCHAR(64)   NOT NULL COMMENT '链路追踪ID',
    `session_id`       VARCHAR(64)   NOT NULL COMMENT '会话ID',
    `user_id`          VARCHAR(64)   NOT NULL COMMENT '用户ID',
    `intent`           VARCHAR(64)   NOT NULL COMMENT '意图编码',
    `approval_type`    ENUM('REFUND', 'EXCHANGE', 'ADDRESS_CHANGE', 'ORDER_CANCEL', 'COMPENSATION', 'OTHER') NOT NULL COMMENT '审批类型',
    `risk_level`       ENUM('L0', 'L1', 'L2', 'L3') NOT NULL COMMENT '风险等级',
    `route_decision`   ENUM('AUTO_REPLY', 'AUTO_EXECUTE', 'CONFIRM_BEFORE_EXECUTE', 'HUMAN_REVIEW', 'HUMAN_TAKEOVER', 'REJECT') NOT NULL COMMENT '路由决策',
    `status`           ENUM('PENDING', 'CLAIMED', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'ESCALATED') NOT NULL DEFAULT 'PENDING' COMMENT '审批状态',
    `priority`         ENUM('LOW', 'NORMAL', 'HIGH', 'URGENT') NOT NULL DEFAULT 'NORMAL' COMMENT '审批优先级',
    `assigned_reviewer` VARCHAR(64)  DEFAULT NULL COMMENT '分配审核人ID',
    `risk_reason`      VARCHAR(1024) DEFAULT NULL COMMENT '触发审批的风险原因',
    `request_payload`  JSON          DEFAULT NULL COMMENT '审批请求数据',
    `context_snapshot` JSON          DEFAULT NULL COMMENT '进入审批时的上下文快照',
    `approval_result`  JSON          DEFAULT NULL COMMENT '审批结果数据',
    `expire_at`        DATETIME(3)   DEFAULT NULL COMMENT '审批过期时间',
    `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `completed_at`     DATETIME(3)   DEFAULT NULL COMMENT '审批完成时间',
    PRIMARY KEY (`approval_id`),
    KEY `idx_ticket` (`ticket_id`),
    KEY `idx_session_created` (`session_id`, `created_at`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_status_priority` (`status`, `priority`),
    KEY `idx_reviewer_status` (`assigned_reviewer`, `status`),
    KEY `idx_intent_status` (`intent`, `status`),
    KEY `idx_trace` (`trace_id`),
    CONSTRAINT `fk_approval_task_ticket`
        FOREIGN KEY (`ticket_id`) REFERENCES `work_order` (`ticket_id`),
    CONSTRAINT `fk_approval_task_session`
        FOREIGN KEY (`session_id`) REFERENCES `cs_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人工审批任务表';

-- ============================================================
-- Approval action
-- ============================================================
CREATE TABLE IF NOT EXISTS `approval_action` (
    `action_id`        VARCHAR(64)   NOT NULL COMMENT '审批动作ID',
    `approval_id`      VARCHAR(64)   NOT NULL COMMENT '审批任务ID',
    `ticket_id`        VARCHAR(64)   NOT NULL COMMENT '关联工单ID',
    `trace_id`         VARCHAR(64)   NOT NULL COMMENT '链路追踪ID',
    `operator_id`      VARCHAR(64)   NOT NULL COMMENT '操作人ID',
    `action_type`      ENUM('CREATE', 'CLAIM', 'APPROVE', 'REJECT', 'MODIFY_AND_APPROVE', 'ESCALATE', 'CANCEL', 'EXPIRE', 'COMMENT') NOT NULL COMMENT '审批动作',
    `before_status`    ENUM('PENDING', 'CLAIMED', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'ESCALATED') DEFAULT NULL COMMENT '操作前状态',
    `after_status`     ENUM('PENDING', 'CLAIMED', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'ESCALATED') DEFAULT NULL COMMENT '操作后状态',
    `comment`          VARCHAR(1024) DEFAULT NULL COMMENT '操作备注',
    `action_data`      JSON          DEFAULT NULL COMMENT '操作数据',
    `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`action_id`),
    KEY `idx_approval_created` (`approval_id`, `created_at`),
    KEY `idx_ticket_created` (`ticket_id`, `created_at`),
    KEY `idx_operator_created` (`operator_id`, `created_at`),
    KEY `idx_action_type` (`action_type`),
    KEY `idx_trace` (`trace_id`),
    CONSTRAINT `fk_approval_action_task`
        FOREIGN KEY (`approval_id`) REFERENCES `approval_task` (`approval_id`),
    CONSTRAINT `fk_approval_action_ticket`
        FOREIGN KEY (`ticket_id`) REFERENCES `work_order` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人工审批操作记录表';

-- ============================================================
-- Human takeover
-- ============================================================
CREATE TABLE IF NOT EXISTS `human_takeover` (
    `takeover_id`      VARCHAR(64)   NOT NULL COMMENT '人工接管ID',
    `ticket_id`        VARCHAR(64)   DEFAULT NULL COMMENT '关联工单ID',
    `trace_id`         VARCHAR(64)   NOT NULL COMMENT '链路追踪ID',
    `session_id`       VARCHAR(64)   NOT NULL COMMENT '会话ID',
    `user_id`          VARCHAR(64)   NOT NULL COMMENT '用户ID',
    `trigger_source`   ENUM('AGENT_LOW_CONFIDENCE', 'RISK_RULE', 'USER_REQUEST', 'SYSTEM_ERROR', 'HUMAN_ASSIGNMENT', 'SLA_TIMEOUT') NOT NULL COMMENT '接管触发来源',
    `status`           ENUM('REQUESTED', 'QUEUED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED') NOT NULL DEFAULT 'REQUESTED' COMMENT '接管状态',
    `priority`         ENUM('LOW', 'NORMAL', 'HIGH', 'URGENT') NOT NULL DEFAULT 'NORMAL' COMMENT '优先级',
    `assigned_agent`   VARCHAR(64)   DEFAULT NULL COMMENT '接管坐席ID',
    `reason`           VARCHAR(1024) DEFAULT NULL COMMENT '接管原因',
    `context_snapshot` JSON          DEFAULT NULL COMMENT '接管时上下文快照',
    `started_at`       DATETIME(3)   DEFAULT NULL COMMENT '坐席开始接管时间',
    `ended_at`         DATETIME(3)   DEFAULT NULL COMMENT '接管结束时间',
    `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`takeover_id`),
    KEY `idx_ticket` (`ticket_id`),
    KEY `idx_session_created` (`session_id`, `created_at`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_status_priority` (`status`, `priority`),
    KEY `idx_agent_status` (`assigned_agent`, `status`),
    KEY `idx_trigger_source` (`trigger_source`),
    KEY `idx_trace` (`trace_id`),
    CONSTRAINT `fk_human_takeover_ticket`
        FOREIGN KEY (`ticket_id`) REFERENCES `work_order` (`ticket_id`),
    CONSTRAINT `fk_human_takeover_session`
        FOREIGN KEY (`session_id`) REFERENCES `cs_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人工接管记录表';
