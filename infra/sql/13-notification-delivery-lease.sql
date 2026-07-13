-- SmartCS notification delivery worker lease.
-- Run this after 11-notification-delivery-status.sql. The guards make this migration repeatable.

USE `smartcs_agent`;

SET @owner_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'notification_event'
      AND column_name = 'delivery_owner'
);
SET @owner_column_sql = IF(
    @owner_column_exists = 0,
    'ALTER TABLE `notification_event` ADD COLUMN `delivery_owner` VARCHAR(64) NULL COMMENT ''当前投递 worker 实例'' AFTER `delivered_at`',
    'SELECT 1'
);
PREPARE owner_column_statement FROM @owner_column_sql;
EXECUTE owner_column_statement;
DEALLOCATE PREPARE owner_column_statement;

SET @lease_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'notification_event'
      AND column_name = 'delivery_lease_until'
);
SET @lease_column_sql = IF(
    @lease_column_exists = 0,
    'ALTER TABLE `notification_event` ADD COLUMN `delivery_lease_until` DATETIME(3) NULL COMMENT ''投递租约截止时间'' AFTER `delivery_owner`',
    'SELECT 1'
);
PREPARE lease_column_statement FROM @lease_column_sql;
EXECUTE lease_column_statement;
DEALLOCATE PREPARE lease_column_statement;

SET @claim_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'notification_event'
      AND index_name = 'idx_notification_event_claim'
);
SET @claim_index_sql = IF(
    @claim_index_exists = 0,
    'ALTER TABLE `notification_event` ADD KEY `idx_notification_event_claim` (`status`, `next_retry_at`, `delivery_lease_until`, `accepted_at`)',
    'SELECT 1'
);
PREPARE claim_index_statement FROM @claim_index_sql;
EXECUTE claim_index_statement;
DEALLOCATE PREPARE claim_index_statement;
