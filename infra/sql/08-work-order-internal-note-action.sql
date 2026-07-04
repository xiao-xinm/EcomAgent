-- SmartCS Workbench internal note action type migration
-- Run this once on existing local databases before using POST /api/workbench/tickets/{ticketId}/notes.

USE `smartcs_agent`;

ALTER TABLE `work_order_action`
    DROP CHECK `chk_work_order_action_type`;

ALTER TABLE `work_order_action`
    MODIFY COLUMN `action_type` ENUM(
        'CREATE',
        'ASSIGN',
        'APPROVE',
        'REJECT',
        'MODIFY_AND_APPROVE',
        'TAKEOVER',
        'ESCALATE',
        'CLOSE',
        'INTERNAL_NOTE'
    ) NOT NULL COMMENT '动作';

ALTER TABLE `work_order_action`
    ADD CONSTRAINT `chk_work_order_action_type`
        CHECK (`action_type` IN (
            'CREATE',
            'ASSIGN',
            'APPROVE',
            'REJECT',
            'MODIFY_AND_APPROVE',
            'TAKEOVER',
            'ESCALATE',
            'CLOSE',
            'INTERNAL_NOTE'
        ));
