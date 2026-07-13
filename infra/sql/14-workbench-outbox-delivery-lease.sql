-- SmartCS Workbench notification outbox delivery worker lease.
-- Run this after 12-workbench-notification-outbox.sql. The guards make this migration repeatable.

USE `smartcs_agent`;

SET @owner_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'workbench_notification_outbox'
      AND column_name = 'delivery_owner'
);
SET @owner_column_sql = IF(
    @owner_column_exists = 0,
    'ALTER TABLE `workbench_notification_outbox` ADD COLUMN `delivery_owner` VARCHAR(64) NULL COMMENT ''当前投递 worker 实例'' AFTER `sent_at`',
    'SELECT 1'
);
PREPARE owner_column_statement FROM @owner_column_sql;
EXECUTE owner_column_statement;
DEALLOCATE PREPARE owner_column_statement;

SET @lease_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'workbench_notification_outbox'
      AND column_name = 'delivery_lease_until'
);
SET @lease_column_sql = IF(
    @lease_column_exists = 0,
    'ALTER TABLE `workbench_notification_outbox` ADD COLUMN `delivery_lease_until` DATETIME(3) NULL COMMENT ''投递租约截止时间'' AFTER `delivery_owner`',
    'SELECT 1'
);
PREPARE lease_column_statement FROM @lease_column_sql;
EXECUTE lease_column_statement;
DEALLOCATE PREPARE lease_column_statement;

SET @claim_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'workbench_notification_outbox'
      AND index_name = 'idx_workbench_notification_outbox_claim'
);
SET @claim_index_sql = IF(
    @claim_index_exists = 0,
    'ALTER TABLE `workbench_notification_outbox` ADD KEY `idx_workbench_notification_outbox_claim` (`status`, `next_attempt_at`, `delivery_lease_until`, `created_at`)',
    'SELECT 1'
);
PREPARE claim_index_statement FROM @claim_index_sql;
EXECUTE claim_index_statement;
DEALLOCATE PREPARE claim_index_statement;
