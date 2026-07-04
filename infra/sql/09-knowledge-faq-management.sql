-- SmartCS Knowledge FAQ management schema.
-- Run this once before using the FAQ management APIs in smartcs-knowledge.

USE `smartcs_agent`;

CREATE TABLE IF NOT EXISTS `knowledge_faq` (
    `faq_id`     VARCHAR(64)  NOT NULL COMMENT 'FAQ ID',
    `question`   VARCHAR(512) NOT NULL COMMENT '标准问题',
    `answer`     TEXT         NOT NULL COMMENT '标准答案',
    `keywords`   JSON         NOT NULL COMMENT '关键词数组',
    `category`   VARCHAR(64)  NOT NULL DEFAULT 'general' COMMENT '分类',
    `status`     ENUM('DRAFT', 'ACTIVE', 'DISABLED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    `priority`   INT          NOT NULL DEFAULT 0 COMMENT '匹配优先级，越大越靠前',
    `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`faq_id`),
    KEY `idx_knowledge_faq_status_priority` (`status`, `priority`, `updated_at`),
    KEY `idx_knowledge_faq_category_status` (`category`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='FAQ 知识库表';

INSERT INTO `knowledge_faq`
    (`faq_id`, `question`, `answer`, `keywords`, `category`, `status`, `priority`)
VALUES
    (
        'faq_refund_arrival',
        '退款多久到账',
        '退款到账时间取决于支付渠道。一般情况下，平台审核通过后会在 1-3 个工作日内原路退回；银行卡或部分第三方渠道可能需要 3-7 个工作日。实际结果以坐席审核和支付渠道回执为准。',
        JSON_ARRAY('退款', '到账', '多久', '几天', '退钱'),
        'after_sale',
        'ACTIVE',
        100
    ),
    (
        'faq_return_policy',
        '退货规则',
        '普通商品支持在售后期内发起退货申请。退款、退货、换货属于敏感售后操作，系统会先创建人工审核工单，由坐席确认订单状态、商品状态和售后原因后再处理。',
        JSON_ARRAY('退货', '规则', '政策', '售后', '条件'),
        'after_sale',
        'ACTIVE',
        90
    ),
    (
        'faq_exchange_policy',
        '换货规则',
        '换货申请需要人工确认库存、订单状态和商品状态。你可以先描述换货原因，系统会创建人工审核工单，坐席审核通过后再推进后续处理。',
        JSON_ARRAY('换货', '规则', '政策', '售后', '条件'),
        'after_sale',
        'ACTIVE',
        80
    ),
    (
        'faq_address_policy',
        '修改地址规则',
        '订单未发货前通常可以修改收货地址；已出库、运输中或已签收的订单需要结合物流状态判断。系统会在自动执行前要求你确认完整的新地址信息。',
        JSON_ARRAY('地址', '收货地址', '修改', '规则', '能不能'),
        'order',
        'ACTIVE',
        70
    ),
    (
        'faq_invoice',
        '发票怎么开',
        '发票通常需要在订单完成后申请。请准备订单号、发票抬头、税号和接收邮箱；后续接入订单服务后可自动查询可开票订单。',
        JSON_ARRAY('发票', '开票', '抬头', '税号'),
        'order',
        'ACTIVE',
        60
    ),
    (
        'faq_freight',
        '运费规则',
        '运费会根据商品、地址、活动和配送方式计算。当前客服系统先提供规则说明；真实运费查询后续会接入订单和物流能力。',
        JSON_ARRAY('运费', '配送费', '邮费', '怎么算', '规则'),
        'logistics',
        'ACTIVE',
        50
    ),
    (
        'faq_price_protection',
        '价格保护规则',
        '如商品支持价格保护，通常需要在价保期内提交申请，并以订单实付金额、活动规则和商品当前价格为准。具体是否可保价需要人工或后续业务系统确认。',
        JSON_ARRAY('保价', '价保', '价格保护', '降价'),
        'after_sale',
        'ACTIVE',
        40
    )
ON DUPLICATE KEY UPDATE
    `question` = VALUES(`question`),
    `answer` = VALUES(`answer`),
    `keywords` = VALUES(`keywords`),
    `category` = VALUES(`category`),
    `status` = VALUES(`status`),
    `priority` = VALUES(`priority`),
    `updated_at` = CURRENT_TIMESTAMP(3);
