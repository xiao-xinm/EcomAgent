-- SmartCS Agent minimal e-commerce domain seed data
-- MySQL 8.x / InnoDB / utf8mb4
--
-- This script is idempotent. It resets only records whose ids start with
-- the fixed e2e business identifiers below.

USE `smartcs_agent`;

SET @order_query_id = 'e2e_order_query_1001';
SET @order_modifiable_id = 'e2e_order_address_1002';
SET @order_refund_id = 'e2e_order_refund_2001';
SET @order_exchange_id = 'e2e_order_exchange_3001';

DELETE FROM `ecom_after_sale_request`
WHERE `order_id` IN (@order_query_id, @order_modifiable_id, @order_refund_id, @order_exchange_id)
   OR `request_id` IN ('e2e_asr_refund_2001', 'e2e_asr_exchange_3001');

DELETE FROM `ecom_order_address`
WHERE `order_id` IN (@order_query_id, @order_modifiable_id, @order_refund_id, @order_exchange_id);

DELETE FROM `ecom_order_item`
WHERE `order_id` IN (@order_query_id, @order_modifiable_id, @order_refund_id, @order_exchange_id);

DELETE FROM `ecom_order`
WHERE `order_id` IN (@order_query_id, @order_modifiable_id, @order_refund_id, @order_exchange_id);

-- Order query scenario: shipped order in transit.
INSERT INTO `ecom_order` (
    `order_id`,
    `order_no`,
    `user_id`,
    `order_status`,
    `pay_status`,
    `logistics_status`,
    `total_amount`,
    `paid_amount`,
    `consignee_name`,
    `consignee_phone`,
    `province`,
    `city`,
    `district`,
    `address_detail`,
    `can_modify_address`,
    `paid_at`,
    `shipped_at`,
    `created_at`
) VALUES (
    @order_query_id,
    'E2E-ORDER-1001',
    'u1001',
    'SHIPPED',
    'PAID',
    'IN_TRANSIT',
    199.00,
    199.00,
    '测试用户A',
    '13800001001',
    '浙江省',
    '杭州市',
    '西湖区',
    '文三路 1001 号',
    0,
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 2 DAY),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 3 DAY)
);

INSERT INTO `ecom_order_item` (
    `item_id`,
    `order_id`,
    `sku_id`,
    `sku_name`,
    `quantity`,
    `unit_price`,
    `total_amount`
) VALUES (
    'e2e_item_query_1001_1',
    @order_query_id,
    'SKU-E2E-COAT-001',
    '测试保暖外套',
    1,
    199.00,
    199.00
);

INSERT INTO `ecom_order_address` (
    `address_id`,
    `order_id`,
    `user_id`,
    `consignee_name`,
    `consignee_phone`,
    `province`,
    `city`,
    `district`,
    `address_detail`,
    `is_current`,
    `source`,
    `created_by`
) VALUES (
    'e2e_addr_query_1001_current',
    @order_query_id,
    'u1001',
    '测试用户A',
    '13800001001',
    '浙江省',
    '杭州市',
    '西湖区',
    '文三路 1001 号',
    1,
    'ORDER_SNAPSHOT',
    'seed'
);

-- Address modification scenario: paid but not shipped, address can be modified automatically.
INSERT INTO `ecom_order` (
    `order_id`,
    `order_no`,
    `user_id`,
    `order_status`,
    `pay_status`,
    `logistics_status`,
    `total_amount`,
    `paid_amount`,
    `consignee_name`,
    `consignee_phone`,
    `province`,
    `city`,
    `district`,
    `address_detail`,
    `can_modify_address`,
    `paid_at`,
    `created_at`
) VALUES (
    @order_modifiable_id,
    'E2E-ORDER-1002',
    'u1001',
    'PAID',
    'PAID',
    'WAITING_SHIP',
    89.90,
    89.90,
    '测试用户A',
    '13800001002',
    '上海市',
    '上海市',
    '浦东新区',
    '世纪大道 1002 号',
    1,
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 4 HOUR),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 HOUR)
);

INSERT INTO `ecom_order_item` (
    `item_id`,
    `order_id`,
    `sku_id`,
    `sku_name`,
    `quantity`,
    `unit_price`,
    `total_amount`
) VALUES (
    'e2e_item_address_1002_1',
    @order_modifiable_id,
    'SKU-E2E-MUG-002',
    '测试马克杯',
    2,
    44.95,
    89.90
);

INSERT INTO `ecom_order_address` (
    `address_id`,
    `order_id`,
    `user_id`,
    `consignee_name`,
    `consignee_phone`,
    `province`,
    `city`,
    `district`,
    `address_detail`,
    `is_current`,
    `source`,
    `created_by`
) VALUES (
    'e2e_addr_address_1002_current',
    @order_modifiable_id,
    'u1001',
    '测试用户A',
    '13800001002',
    '上海市',
    '上海市',
    '浦东新区',
    '世纪大道 1002 号',
    1,
    'ORDER_SNAPSHOT',
    'seed'
);

-- Refund scenario: paid order with a pending after-sale request.
INSERT INTO `ecom_order` (
    `order_id`,
    `order_no`,
    `user_id`,
    `order_status`,
    `pay_status`,
    `logistics_status`,
    `total_amount`,
    `paid_amount`,
    `consignee_name`,
    `consignee_phone`,
    `province`,
    `city`,
    `district`,
    `address_detail`,
    `can_modify_address`,
    `paid_at`,
    `created_at`
) VALUES (
    @order_refund_id,
    'E2E-ORDER-2001',
    'u_e2e_1002',
    'PAID',
    'PAID',
    'WAITING_SHIP',
    299.00,
    299.00,
    '测试用户B',
    '13800002001',
    '广东省',
    '深圳市',
    '南山区',
    '科技园 2001 号',
    1,
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 6 HOUR),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 7 HOUR)
);

INSERT INTO `ecom_order_item` (
    `item_id`,
    `order_id`,
    `sku_id`,
    `sku_name`,
    `quantity`,
    `unit_price`,
    `total_amount`,
    `after_sale_status`
) VALUES (
    'e2e_item_refund_2001_1',
    @order_refund_id,
    'SKU-E2E-BAG-003',
    '测试通勤包',
    1,
    299.00,
    299.00,
    'REFUNDING'
);

INSERT INTO `ecom_order_address` (
    `address_id`,
    `order_id`,
    `user_id`,
    `consignee_name`,
    `consignee_phone`,
    `province`,
    `city`,
    `district`,
    `address_detail`,
    `is_current`,
    `source`,
    `created_by`
) VALUES (
    'e2e_addr_refund_2001_current',
    @order_refund_id,
    'u_e2e_1002',
    '测试用户B',
    '13800002001',
    '广东省',
    '深圳市',
    '南山区',
    '科技园 2001 号',
    1,
    'ORDER_SNAPSHOT',
    'seed'
);

INSERT INTO `ecom_after_sale_request` (
    `request_id`,
    `order_id`,
    `item_id`,
    `user_id`,
    `request_type`,
    `status`,
    `reason_code`,
    `reason_text`,
    `requested_amount`,
    `evidence`,
    `created_by`
) VALUES (
    'e2e_asr_refund_2001',
    @order_refund_id,
    'e2e_item_refund_2001_1',
    'u_e2e_1002',
    'REFUND',
    'PENDING_REVIEW',
    'NO_LONGER_NEEDED',
    '不想要了',
    299.00,
    JSON_OBJECT('seed', true),
    'seed'
);

-- Exchange scenario: signed order with exchange request.
INSERT INTO `ecom_order` (
    `order_id`,
    `order_no`,
    `user_id`,
    `order_status`,
    `pay_status`,
    `logistics_status`,
    `total_amount`,
    `paid_amount`,
    `consignee_name`,
    `consignee_phone`,
    `province`,
    `city`,
    `district`,
    `address_detail`,
    `can_modify_address`,
    `paid_at`,
    `shipped_at`,
    `signed_at`,
    `created_at`
) VALUES (
    @order_exchange_id,
    'E2E-ORDER-3001',
    'u_e2e_1003',
    'SIGNED',
    'PAID',
    'SIGNED',
    129.00,
    129.00,
    '测试用户C',
    '13800003001',
    '江苏省',
    '南京市',
    '鼓楼区',
    '中山路 3001 号',
    0,
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 DAY),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 4 DAY),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY),
    DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 6 DAY)
);

INSERT INTO `ecom_order_item` (
    `item_id`,
    `order_id`,
    `sku_id`,
    `sku_name`,
    `quantity`,
    `unit_price`,
    `total_amount`,
    `after_sale_status`
) VALUES (
    'e2e_item_exchange_3001_1',
    @order_exchange_id,
    'SKU-E2E-SHOES-004',
    '测试运动鞋',
    1,
    129.00,
    129.00,
    'EXCHANGING'
);

INSERT INTO `ecom_order_address` (
    `address_id`,
    `order_id`,
    `user_id`,
    `consignee_name`,
    `consignee_phone`,
    `province`,
    `city`,
    `district`,
    `address_detail`,
    `is_current`,
    `source`,
    `created_by`
) VALUES (
    'e2e_addr_exchange_3001_current',
    @order_exchange_id,
    'u_e2e_1003',
    '测试用户C',
    '13800003001',
    '江苏省',
    '南京市',
    '鼓楼区',
    '中山路 3001 号',
    1,
    'ORDER_SNAPSHOT',
    'seed'
);

INSERT INTO `ecom_after_sale_request` (
    `request_id`,
    `order_id`,
    `item_id`,
    `user_id`,
    `request_type`,
    `status`,
    `reason_code`,
    `reason_text`,
    `evidence`,
    `created_by`
) VALUES (
    'e2e_asr_exchange_3001',
    @order_exchange_id,
    'e2e_item_exchange_3001_1',
    'u_e2e_1003',
    'EXCHANGE',
    'PENDING_REVIEW',
    'SIZE_NOT_FIT',
    '尺码不合适',
    JSON_OBJECT('seed', true),
    'seed'
);
