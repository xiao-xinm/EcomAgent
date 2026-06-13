-- SmartCS Agent phase-2 skill orchestration schema
-- MySQL 8.x / InnoDB / utf8mb4

USE `smartcs_agent`;

-- ============================================================
-- Skill registry
-- ============================================================
CREATE TABLE IF NOT EXISTS `skill_registry` (
    `skill_id`          VARCHAR(64)   NOT NULL COMMENT '技能ID',
    `skill_name`        VARCHAR(128)  NOT NULL COMMENT '技能名称',
    `intent`            VARCHAR(64)   NOT NULL COMMENT '关联意图编码',
    `base_risk_level`   ENUM('L0', 'L1', 'L2', 'L3') NOT NULL COMMENT '基础风险等级',
    `execution_type`    ENUM('SYNC', 'ASYNC', 'REVIEW_ONLY') NOT NULL DEFAULT 'SYNC' COMMENT '执行类型',
    `enabled`           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否启用',
    `status`            ENUM('DRAFT', 'ACTIVE', 'DISABLED', 'DEPRECATED') NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    `description`       VARCHAR(512)  DEFAULT NULL COMMENT '技能说明',
    `pre_conditions`    JSON          DEFAULT NULL COMMENT '前置条件，声明式表达式',
    `retry_policy`      JSON          DEFAULT NULL COMMENT '重试策略',
    `timeout_ms`        INT           NOT NULL DEFAULT 30000 COMMENT '技能整体超时时间，单位毫秒',
    `owner`             VARCHAR(64)   DEFAULT NULL COMMENT '技能负责人',
    `version`           VARCHAR(32)   NOT NULL DEFAULT '1.0.0' COMMENT '技能版本',
    `created_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`skill_id`),
    UNIQUE KEY `uk_skill_intent_version` (`intent`, `version`),
    KEY `idx_intent_enabled` (`intent`, `enabled`),
    KEY `idx_status` (`status`),
    KEY `idx_risk_level` (`base_risk_level`),
    CONSTRAINT `chk_skill_base_risk_level`
        CHECK (`base_risk_level` IN ('L0', 'L1', 'L2', 'L3')),
    CONSTRAINT `chk_skill_execution_type`
        CHECK (`execution_type` IN ('SYNC', 'ASYNC', 'REVIEW_ONLY')),
    CONSTRAINT `chk_skill_enabled`
        CHECK (`enabled` IN (0, 1)),
    CONSTRAINT `chk_skill_status`
        CHECK (`status` IN ('DRAFT', 'ACTIVE', 'DISABLED', 'DEPRECATED')),
    CONSTRAINT `chk_skill_timeout`
        CHECK (`timeout_ms` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技能注册表';

-- ============================================================
-- Skill slot definition
-- ============================================================
CREATE TABLE IF NOT EXISTS `skill_slot` (
    `slot_id`                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '槽位定义ID',
    `skill_id`                VARCHAR(64)   NOT NULL COMMENT '技能ID',
    `slot_name`               VARCHAR(64)   NOT NULL COMMENT '槽位名称',
    `slot_type`               ENUM('STRING', 'NUMBER', 'BOOLEAN', 'ENUM', 'DATE', 'DATETIME', 'ADDRESS', 'PHONE', 'ORDER_ID', 'AMOUNT', 'JSON') NOT NULL COMMENT '槽位类型',
    `required`                TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否必填',
    `source_priority`         JSON          DEFAULT NULL COMMENT '填槽来源优先级，如 extracted/context/default',
    `enum_values`             JSON          DEFAULT NULL COMMENT '枚举值列表',
    `validation_rule`         VARCHAR(512)  DEFAULT NULL COMMENT '校验规则表达式或正则',
    `clarification_template`  VARCHAR(512)  DEFAULT NULL COMMENT '缺失槽位追问模板',
    `default_value`           JSON          DEFAULT NULL COMMENT '默认值',
    `display_order`           INT           NOT NULL DEFAULT 0 COMMENT '追问或展示顺序',
    `created_at`              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`slot_id`),
    UNIQUE KEY `uk_skill_slot` (`skill_id`, `slot_name`),
    KEY `idx_skill_required_order` (`skill_id`, `required`, `display_order`),
    CONSTRAINT `fk_skill_slot_skill`
        FOREIGN KEY (`skill_id`) REFERENCES `skill_registry` (`skill_id`)
        ON DELETE CASCADE,
    CONSTRAINT `chk_skill_slot_type`
        CHECK (`slot_type` IN ('STRING', 'NUMBER', 'BOOLEAN', 'ENUM', 'DATE', 'DATETIME', 'ADDRESS', 'PHONE', 'ORDER_ID', 'AMOUNT', 'JSON')),
    CONSTRAINT `chk_skill_slot_required`
        CHECK (`required` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技能槽位定义表';

-- ============================================================
-- Skill API orchestration step
-- ============================================================
CREATE TABLE IF NOT EXISTS `skill_api_step` (
    `step_id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '技能调用步骤ID',
    `skill_id`            VARCHAR(64)   NOT NULL COMMENT '技能ID',
    `step_no`             INT           NOT NULL COMMENT '步骤序号，从1开始',
    `step_name`           VARCHAR(128)  NOT NULL COMMENT '步骤名称',
    `api_name`            VARCHAR(128)  NOT NULL COMMENT 'API 名称',
    `http_method`         ENUM('GET', 'POST', 'PUT', 'PATCH', 'DELETE') NOT NULL COMMENT 'HTTP 方法',
    `endpoint`            VARCHAR(512)  NOT NULL COMMENT '业务 API 地址或服务路径',
    `params_mapping`      JSON          DEFAULT NULL COMMENT '请求参数映射',
    `headers_mapping`     JSON          DEFAULT NULL COMMENT '请求头映射',
    `body_template`       JSON          DEFAULT NULL COMMENT '请求体模板',
    `result_mapping`      JSON          DEFAULT NULL COMMENT '响应结果映射',
    `required`            TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否关键步骤',
    `rollback_endpoint`   VARCHAR(512)  DEFAULT NULL COMMENT '回滚 API 地址',
    `rollback_mapping`    JSON          DEFAULT NULL COMMENT '回滚参数映射',
    `timeout_ms`          INT           NOT NULL DEFAULT 10000 COMMENT '单步骤超时时间，单位毫秒',
    `retry_policy`        JSON          DEFAULT NULL COMMENT '单步骤重试策略',
    `created_at`          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`step_id`),
    UNIQUE KEY `uk_skill_step_no` (`skill_id`, `step_no`),
    KEY `idx_skill_required` (`skill_id`, `required`),
    CONSTRAINT `fk_skill_api_step_skill`
        FOREIGN KEY (`skill_id`) REFERENCES `skill_registry` (`skill_id`)
        ON DELETE CASCADE,
    CONSTRAINT `chk_skill_step_no`
        CHECK (`step_no` > 0),
    CONSTRAINT `chk_skill_api_http_method`
        CHECK (`http_method` IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE')),
    CONSTRAINT `chk_skill_api_required`
        CHECK (`required` IN (0, 1)),
    CONSTRAINT `chk_skill_api_timeout`
        CHECK (`timeout_ms` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技能API编排步骤表';

-- ============================================================
-- Skill execution log
-- ============================================================
CREATE TABLE IF NOT EXISTS `skill_execution_log` (
    `execution_id`      VARCHAR(64)   NOT NULL COMMENT '技能执行ID',
    `trace_id`          VARCHAR(64)   NOT NULL COMMENT '链路追踪ID',
    `session_id`        VARCHAR(64)   NOT NULL COMMENT '会话ID',
    `message_id`        VARCHAR(64)   DEFAULT NULL COMMENT '触发消息ID',
    `skill_id`          VARCHAR(64)   NOT NULL COMMENT '技能ID',
    `intent`            VARCHAR(64)   NOT NULL COMMENT '意图编码',
    `user_id`           VARCHAR(64)   NOT NULL COMMENT '用户ID',
    `risk_level`        ENUM('L0', 'L1', 'L2', 'L3') NOT NULL COMMENT '执行时风险等级',
    `route_decision`    ENUM('AUTO_REPLY', 'AUTO_EXECUTE', 'CONFIRM_BEFORE_EXECUTE', 'HUMAN_REVIEW', 'HUMAN_TAKEOVER', 'REJECT') NOT NULL COMMENT '执行时路由决策',
    `status`            ENUM('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'REVIEW_REQUIRED', 'ROLLBACK_SUCCEEDED', 'ROLLBACK_FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '执行状态',
    `request_snapshot`  JSON          DEFAULT NULL COMMENT '执行请求快照',
    `step_results`      JSON          DEFAULT NULL COMMENT '步骤执行结果',
    `response_snapshot` JSON          DEFAULT NULL COMMENT '执行响应快照',
    `error_code`        VARCHAR(64)   DEFAULT NULL COMMENT '错误码',
    `error_message`     VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `started_at`        DATETIME(3)   DEFAULT NULL COMMENT '开始执行时间',
    `finished_at`       DATETIME(3)   DEFAULT NULL COMMENT '结束执行时间',
    `created_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`execution_id`),
    KEY `idx_session_created` (`session_id`, `created_at`),
    KEY `idx_skill_status` (`skill_id`, `status`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_trace` (`trace_id`),
    KEY `idx_message` (`message_id`),
    CONSTRAINT `fk_skill_execution_session`
        FOREIGN KEY (`session_id`) REFERENCES `cs_session` (`session_id`),
    CONSTRAINT `fk_skill_execution_message`
        FOREIGN KEY (`message_id`) REFERENCES `cs_message` (`message_id`),
    CONSTRAINT `fk_skill_execution_skill`
        FOREIGN KEY (`skill_id`) REFERENCES `skill_registry` (`skill_id`),
    CONSTRAINT `chk_skill_execution_risk_level`
        CHECK (`risk_level` IN ('L0', 'L1', 'L2', 'L3')),
    CONSTRAINT `chk_skill_execution_route_decision`
        CHECK (`route_decision` IN ('AUTO_REPLY', 'AUTO_EXECUTE', 'CONFIRM_BEFORE_EXECUTE', 'HUMAN_REVIEW', 'HUMAN_TAKEOVER', 'REJECT')),
    CONSTRAINT `chk_skill_execution_status`
        CHECK (`status` IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'REVIEW_REQUIRED', 'ROLLBACK_SUCCEEDED', 'ROLLBACK_FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技能执行日志表';
