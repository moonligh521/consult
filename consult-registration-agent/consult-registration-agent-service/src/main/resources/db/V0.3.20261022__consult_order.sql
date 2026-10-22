-- V0.3.20261022 咨询下单表。卡片确认后选四档，演示环境直接记已支付。
-- 人工在目标库执行。服务启动不自动建表。

CREATE TABLE IF NOT EXISTS consult_order (
    id                VARCHAR(64)    NOT NULL COMMENT '订单编号',
    consultation_id   VARCHAR(64)    NOT NULL COMMENT '所属咨询编号',
    package_code      TINYINT        NOT NULL COMMENT '档位。1普通咨询 2极速咨询 3三甲医生 4权威专家',
    package_name      VARCHAR(64)    NOT NULL COMMENT '下单时的档位名称',
    origin_amount     DECIMAL(10, 2) NOT NULL COMMENT '划线价',
    pay_amount        DECIMAL(10, 2) NOT NULL COMMENT '应付金额',
    discount_amount   DECIMAL(10, 2) NOT NULL COMMENT '立减金额',
    pay_status        TINYINT        NOT NULL COMMENT '支付状态。1待支付 2已支付。演示下单后为2',
    paid_at           DATETIME(3)    NULL     COMMENT '记为已支付的时间',
    remark            VARCHAR(400)   NULL     COMMENT '备注',
    created_by        VARCHAR(100)   NULL     COMMENT '创建人',
    updated_by        VARCHAR(100)   NULL     COMMENT '修改人',
    created_at        DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted        BIGINT         NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除；删除后写入非0唯一值，保证业务唯一键能再用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consult_order_live (consultation_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='咨询下单。演示支付，不接真实渠道。删除走逻辑删除';
