-- SmartCS Workbench notification transactional outbox.
-- Run this before enabling SMARTCS_NOTIFICATION_OUTBOX_ENABLED.

USE `smartcs_agent`;

CREATE TABLE IF NOT EXISTS `workbench_notification_outbox` (
    `event_id`        VARCHAR(64)  NOT NULL COMMENT '稳定通知事件ID',
    `trace_id`        VARCHAR(64)  NULL COMMENT '链路追踪ID',
    `ticket_id`       VARCHAR(64)  NULL COMMENT '工单ID',
    `event_type`      VARCHAR(64)  NOT NULL COMMENT '通知事件类型',
    `channel`         VARCHAR(64)  NOT NULL COMMENT '通知渠道',
    `event_payload`   JSON         NOT NULL COMMENT '完整通知事件请求',
    `status`          ENUM('PENDING', 'SENT', 'FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
    `attempt_count`   INT          NOT NULL DEFAULT 0 COMMENT '已失败尝试次数',
    `last_error`      VARCHAR(1000) NULL COMMENT '最近一次失败原因',
    `next_attempt_at` DATETIME(3)  NULL COMMENT '下次尝试时间',
    `sent_at`         DATETIME(3)  NULL COMMENT '投递成功时间',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`event_id`),
    KEY `idx_workbench_notification_outbox_due` (`status`, `next_attempt_at`, `created_at`),
    KEY `idx_workbench_notification_outbox_ticket` (`ticket_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Workbench 通知事务 outbox';
