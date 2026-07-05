-- SmartCS notification delivery status extension.
-- Run this after 10-notification-event-store.sql.

USE `smartcs_agent`;

ALTER TABLE `notification_event`
    ADD COLUMN `retry_count` INT NOT NULL DEFAULT 0 COMMENT '投递失败重试次数' AFTER `status`,
    ADD COLUMN `last_error` VARCHAR(1000) NULL COMMENT '最近一次投递失败原因' AFTER `retry_count`,
    ADD COLUMN `next_retry_at` DATETIME(3) NULL COMMENT '下次重试时间' AFTER `last_error`,
    ADD COLUMN `delivered_at` DATETIME(3) NULL COMMENT '投递成功时间' AFTER `next_retry_at`,
    ADD KEY `idx_notification_event_retry` (`status`, `next_retry_at`);
