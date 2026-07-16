-- SmartCS Workbench ticket SSE incremental scan indexes.
-- The guards make this migration repeatable on an existing smartcs_agent database.

USE `smartcs_agent`;

SET @work_order_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'work_order'
      AND index_name = 'idx_updated_ticket'
);
SET @work_order_index_sql = IF(
    @work_order_index_exists = 0,
    'ALTER TABLE `work_order` ADD KEY `idx_updated_ticket` (`updated_at`, `ticket_id`)',
    'SELECT 1'
);
PREPARE work_order_index_statement FROM @work_order_index_sql;
EXECUTE work_order_index_statement;
DEALLOCATE PREPARE work_order_index_statement;

SET @audit_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'audit_log'
      AND index_name = 'idx_occurred_event'
);
SET @audit_index_sql = IF(
    @audit_index_exists = 0,
    'ALTER TABLE `audit_log` ADD KEY `idx_occurred_event` (`occurred_at`, `event_id`)',
    'SELECT 1'
);
PREPARE audit_index_statement FROM @audit_index_sql;
EXECUTE audit_index_statement;
DEALLOCATE PREPARE audit_index_statement;
