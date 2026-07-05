-- SmartCS notification event store.
-- Run this once before using smartcs-notification event persistence and query APIs.

USE `smartcs_agent`;

CREATE TABLE IF NOT EXISTS `notification_event` (
    `event_id`          VARCHAR(64)  NOT NULL COMMENT '通知事件ID',
    `trace_id`          VARCHAR(64)  NULL COMMENT '链路追踪ID',
    `source_service`    VARCHAR(64)  NULL COMMENT '来源服务',
    `event_type`        VARCHAR(64)  NOT NULL COMMENT '事件类型',
    `recipient_user_id` VARCHAR(64)  NULL COMMENT '接收用户ID',
    `session_id`        VARCHAR(64)  NULL COMMENT '会话ID',
    `ticket_id`         VARCHAR(64)  NULL COMMENT '工单ID',
    `operator_id`       VARCHAR(64)  NULL COMMENT '操作人ID',
    `channel`           VARCHAR(64)  NOT NULL DEFAULT 'USER_SESSION' COMMENT '通知渠道',
    `title`             VARCHAR(255) NULL COMMENT '通知标题',
    `content`           TEXT         NULL COMMENT '通知内容',
    `payload`           JSON         NOT NULL COMMENT '事件载荷',
    `status`            ENUM('ACCEPTED', 'DELIVERED', 'FAILED') NOT NULL DEFAULT 'ACCEPTED' COMMENT '事件状态',
    `occurred_at`       DATETIME(3)  NOT NULL COMMENT '业务发生时间',
    `accepted_at`       DATETIME(3)  NOT NULL COMMENT '通知服务接收时间',
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`event_id`),
    KEY `idx_notification_event_ticket` (`ticket_id`, `accepted_at`),
    KEY `idx_notification_event_recipient` (`recipient_user_id`, `accepted_at`),
    KEY `idx_notification_event_type_status` (`event_type`, `status`, `accepted_at`),
    KEY `idx_notification_event_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知事件表';
