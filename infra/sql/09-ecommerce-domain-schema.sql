-- SmartCS Agent phase-4 minimal e-commerce domain schema
-- MySQL 8.x / InnoDB / utf8mb4
--
-- This schema is the first real business data surface used by skills.
-- It is intentionally small: order query, address modification, and
-- after-sale review are supported without implementing real payment,
-- warehouse, logistics, refund, or exchange integrations.

USE `smartcs_agent`;

-- ============================================================
-- E-commerce order
-- ============================================================
CREATE TABLE IF NOT EXISTS `ecom_order` (
    `order_id`             VARCHAR(64)   NOT NULL COMMENT '订单ID',
    `order_no`             VARCHAR(64)   NOT NULL COMMENT '展示给用户的订单号',
    `user_id`              VARCHAR(64)   NOT NULL COMMENT '用户ID',
    `order_status`         ENUM('CREATED', 'PAID', 'PACKING', 'SHIPPED', 'SIGNED', 'COMPLETED', 'CANCELLED', 'REFUNDING', 'REFUNDED', 'CLOSED') NOT NULL COMMENT '订单状态',
    `pay_status`           ENUM('UNPAID', 'PAID', 'PARTIAL_REFUNDED', 'REFUNDED', 'CLOSED') NOT NULL DEFAULT 'UNPAID' COMMENT '支付状态',
    `logistics_status`     ENUM('NONE', 'WAITING_SHIP', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'SIGNED', 'EXCEPTION') NOT NULL DEFAULT 'NONE' COMMENT '物流状态',
    `total_amount`         DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '订单总金额',
    `paid_amount`          DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额',
    `currency`             CHAR(3)       NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `consignee_name`       VARCHAR(64)   DEFAULT NULL COMMENT '当前收货人姓名，生产写入前应脱敏',
    `consignee_phone`      VARCHAR(32)   DEFAULT NULL COMMENT '当前收货人手机号，生产写入前应脱敏',
    `province`             VARCHAR(64)   DEFAULT NULL COMMENT '省',
    `city`                 VARCHAR(64)   DEFAULT NULL COMMENT '市',
    `district`             VARCHAR(64)   DEFAULT NULL COMMENT '区县',
    `address_detail`       VARCHAR(512)  DEFAULT NULL COMMENT '详细地址，生产写入前应脱敏',
    `can_modify_address`   TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '当前是否允许自动改地址',
    `paid_at`              DATETIME(3)   DEFAULT NULL COMMENT '支付时间',
    `shipped_at`           DATETIME(3)   DEFAULT NULL COMMENT '发货时间',
    `signed_at`            DATETIME(3)   DEFAULT NULL COMMENT '签收时间',
    `created_at`           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`order_id`),
    UNIQUE KEY `uk_ecom_order_no` (`order_no`),
    KEY `idx_ecom_order_user_created` (`user_id`, `created_at`),
    KEY `idx_ecom_order_user_status` (`user_id`, `order_status`),
    KEY `idx_ecom_order_logistics` (`logistics_status`),
    CONSTRAINT `chk_ecom_order_amount`
        CHECK (`total_amount` >= 0 AND `paid_amount` >= 0),
    CONSTRAINT `chk_ecom_order_can_modify_address`
        CHECK (`can_modify_address` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电商订单表';

-- ============================================================
-- E-commerce order item
-- ============================================================
CREATE TABLE IF NOT EXISTS `ecom_order_item` (
    `item_id`              VARCHAR(64)   NOT NULL COMMENT '订单明细ID',
    `order_id`             VARCHAR(64)   NOT NULL COMMENT '订单ID',
    `sku_id`               VARCHAR(64)   NOT NULL COMMENT 'SKU ID',
    `sku_name`             VARCHAR(256)  NOT NULL COMMENT 'SKU 名称',
    `quantity`             INT           NOT NULL COMMENT '购买数量',
    `unit_price`           DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '单价',
    `total_amount`         DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '明细总金额',
    `after_sale_status`    ENUM('NONE', 'REFUNDING', 'EXCHANGING', 'REFUNDED', 'EXCHANGED') NOT NULL DEFAULT 'NONE' COMMENT '售后状态',
    `created_at`           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`item_id`),
    KEY `idx_ecom_order_item_order` (`order_id`),
    KEY `idx_ecom_order_item_sku` (`sku_id`),
    CONSTRAINT `fk_ecom_order_item_order`
        FOREIGN KEY (`order_id`) REFERENCES `ecom_order` (`order_id`),
    CONSTRAINT `chk_ecom_order_item_quantity`
        CHECK (`quantity` > 0),
    CONSTRAINT `chk_ecom_order_item_amount`
        CHECK (`unit_price` >= 0 AND `total_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电商订单明细表';

-- ============================================================
-- Order address history
-- ============================================================
CREATE TABLE IF NOT EXISTS `ecom_order_address` (
    `address_id`           VARCHAR(64)   NOT NULL COMMENT '订单地址记录ID',
    `order_id`             VARCHAR(64)   NOT NULL COMMENT '订单ID',
    `user_id`              VARCHAR(64)   NOT NULL COMMENT '用户ID',
    `consignee_name`       VARCHAR(64)   NOT NULL COMMENT '收货人姓名，生产写入前应脱敏',
    `consignee_phone`      VARCHAR(32)   NOT NULL COMMENT '收货手机号，生产写入前应脱敏',
    `province`             VARCHAR(64)   NOT NULL COMMENT '省',
    `city`                 VARCHAR(64)   NOT NULL COMMENT '市',
    `district`             VARCHAR(64)   NOT NULL COMMENT '区县',
    `address_detail`       VARCHAR(512)  NOT NULL COMMENT '详细地址，生产写入前应脱敏',
    `postal_code`          VARCHAR(16)   DEFAULT NULL COMMENT '邮编',
    `is_current`           TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否为当前地址',
    `source`               ENUM('ORDER_SNAPSHOT', 'USER_MODIFY', 'AGENT_MODIFY', 'HUMAN_MODIFY') NOT NULL DEFAULT 'ORDER_SNAPSHOT' COMMENT '地址来源',
    `source_message_id`    VARCHAR(64)   DEFAULT NULL COMMENT '触发地址变更的消息ID',
    `source_ticket_id`     VARCHAR(64)   DEFAULT NULL COMMENT '触发人工地址变更的工单ID',
    `change_reason`        VARCHAR(512)  DEFAULT NULL COMMENT '地址变更原因',
    `created_by`           VARCHAR(64)   DEFAULT NULL COMMENT '创建人或系统标识',
    `created_at`           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`address_id`),
    KEY `idx_ecom_order_address_order_current` (`order_id`, `is_current`),
    KEY `idx_ecom_order_address_user_created` (`user_id`, `created_at`),
    KEY `idx_ecom_order_address_ticket` (`source_ticket_id`),
    CONSTRAINT `fk_ecom_order_address_order`
        FOREIGN KEY (`order_id`) REFERENCES `ecom_order` (`order_id`),
    CONSTRAINT `fk_ecom_order_address_message`
        FOREIGN KEY (`source_message_id`) REFERENCES `cs_message` (`message_id`),
    CONSTRAINT `fk_ecom_order_address_ticket`
        FOREIGN KEY (`source_ticket_id`) REFERENCES `work_order` (`ticket_id`),
    CONSTRAINT `chk_ecom_order_address_current`
        CHECK (`is_current` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单地址历史表';

-- ============================================================
-- After-sale request
-- ============================================================
CREATE TABLE IF NOT EXISTS `ecom_after_sale_request` (
    `request_id`           VARCHAR(64)   NOT NULL COMMENT '售后申请ID',
    `order_id`             VARCHAR(64)   NOT NULL COMMENT '订单ID',
    `item_id`              VARCHAR(64)   DEFAULT NULL COMMENT '订单明细ID，整单售后时为空',
    `user_id`              VARCHAR(64)   NOT NULL COMMENT '用户ID',
    `request_type`         ENUM('REFUND', 'EXCHANGE') NOT NULL COMMENT '售后类型',
    `status`               ENUM('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'PROCESSING', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'DRAFT' COMMENT '售后状态',
    `source_ticket_id`     VARCHAR(64)   DEFAULT NULL COMMENT '来源工单ID',
    `source_approval_id`   VARCHAR(64)   DEFAULT NULL COMMENT '来源审批任务ID',
    `reason_code`          VARCHAR(64)   DEFAULT NULL COMMENT '售后原因编码',
    `reason_text`          VARCHAR(512)  DEFAULT NULL COMMENT '售后原因说明',
    `requested_amount`     DECIMAL(12,2) DEFAULT NULL COMMENT '申请退款金额，换货可为空',
    `evidence`             JSON          DEFAULT NULL COMMENT '凭证信息，如图片、说明等元数据',
    `review_result`        JSON          DEFAULT NULL COMMENT '人工审核结果快照',
    `created_by`           VARCHAR(64)   DEFAULT NULL COMMENT '创建人或系统标识',
    `reviewed_by`          VARCHAR(64)   DEFAULT NULL COMMENT '审核人',
    `reviewed_at`          DATETIME(3)   DEFAULT NULL COMMENT '审核时间',
    `completed_at`         DATETIME(3)   DEFAULT NULL COMMENT '售后完成时间',
    `created_at`           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`request_id`),
    KEY `idx_after_sale_order` (`order_id`),
    KEY `idx_after_sale_item` (`item_id`),
    KEY `idx_after_sale_user_status` (`user_id`, `status`),
    KEY `idx_after_sale_ticket` (`source_ticket_id`),
    KEY `idx_after_sale_approval` (`source_approval_id`),
    CONSTRAINT `fk_after_sale_order`
        FOREIGN KEY (`order_id`) REFERENCES `ecom_order` (`order_id`),
    CONSTRAINT `fk_after_sale_item`
        FOREIGN KEY (`item_id`) REFERENCES `ecom_order_item` (`item_id`),
    CONSTRAINT `fk_after_sale_ticket`
        FOREIGN KEY (`source_ticket_id`) REFERENCES `work_order` (`ticket_id`),
    CONSTRAINT `fk_after_sale_approval`
        FOREIGN KEY (`source_approval_id`) REFERENCES `approval_task` (`approval_id`),
    CONSTRAINT `chk_after_sale_requested_amount`
        CHECK (`requested_amount` IS NULL OR `requested_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电商售后申请表';
