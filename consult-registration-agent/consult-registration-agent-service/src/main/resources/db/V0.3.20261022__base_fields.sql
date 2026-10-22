-- V0.3.20261022 公司基础字段：备注、创建人、修改人、逻辑删除。
-- 已建过 V0.3.20261021 的库执行本文件。新库只跑建表脚本，不要再跑本文件。
-- is_deleted：0 未删除；删除后写入非 0 唯一值，原话唯一键 (consultation_id, message_id, is_deleted) 仍可再用。

ALTER TABLE consultation_session
    ADD COLUMN remark VARCHAR(400) NULL COMMENT '备注' AFTER last_evaluated_seq,
    ADD COLUMN created_by VARCHAR(100) NULL COMMENT '创建人' AFTER remark,
    ADD COLUMN updated_by VARCHAR(100) NULL COMMENT '修改人' AFTER created_by,
    ADD COLUMN is_deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除；删除后写入非0唯一值，保证业务唯一键能再用' AFTER updated_at;

ALTER TABLE consultation_message
    ADD COLUMN remark VARCHAR(400) NULL COMMENT '备注' AFTER received_at,
    ADD COLUMN created_by VARCHAR(100) NULL COMMENT '创建人' AFTER remark,
    ADD COLUMN updated_by VARCHAR(100) NULL COMMENT '修改人' AFTER created_by,
    ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' AFTER updated_by,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' AFTER created_at,
    ADD COLUMN is_deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除；删除后写入非0唯一值，保证业务唯一键能再用' AFTER updated_at;

ALTER TABLE consultation_message DROP INDEX uk_consultation_message;

ALTER TABLE consultation_message
    ADD UNIQUE KEY uk_consultation_message (consultation_id, message_id, is_deleted);

ALTER TABLE safety_decision_audit
    ADD COLUMN remark VARCHAR(400) NULL COMMENT '备注' AFTER decision_time,
    ADD COLUMN created_by VARCHAR(100) NULL COMMENT '创建人' AFTER remark,
    ADD COLUMN updated_by VARCHAR(100) NULL COMMENT '修改人' AFTER created_by,
    ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间' AFTER updated_by,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间' AFTER created_at,
    ADD COLUMN is_deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除；删除后写入非0唯一值，保证业务唯一键能再用' AFTER updated_at;
