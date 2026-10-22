-- V0.5.20261022 挂号错别字与不确定定科标记。人工在目标库执行。
-- 与科室关键词同一份匹配快照、同一把延时双删。不要只改库不删 Redis。
-- 错词不得是急症 DRAFT 别名（胸疼/呼息困难/口唇发子等），写入走应用校验。

CREATE TABLE IF NOT EXISTS consult_match_typo (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    from_word      VARCHAR(32)  NOT NULL COMMENT '去空格后的错词',
    to_word        VARCHAR(32)  NOT NULL COMMENT '对应目录正词',
    remark         VARCHAR(400) NULL     COMMENT '备注',
    created_by     VARCHAR(100) NULL     COMMENT '创建人',
    updated_by     VARCHAR(100) NULL     COMMENT '修改人',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted     BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consult_match_typo (from_word, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='挂号匹配错别字。急症闸门不走本表';

CREATE TABLE IF NOT EXISTS consult_uncertain_marker (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    marker         VARCHAR(64)  NOT NULL COMMENT '患者明确说定不了科的标记',
    remark         VARCHAR(400) NULL     COMMENT '备注',
    created_by     VARCHAR(100) NULL     COMMENT '创建人',
    updated_by     VARCHAR(100) NULL     COMMENT '修改人',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted     BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consult_uncertain_marker (marker, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不确定定科标记。命中则兜底全科';

INSERT IGNORE INTO consult_match_typo (from_word, to_word, remark) VALUES
('刻嗽', '咳嗽', '评测口语'),
('头通', '头痛', '评测口语'),
('腹疼', '腹痛', '评测口语'),
('腹泄', '腹泻', '评测口语'),
('胃疼', '胃痛', '评测口语'),
('失眼', '失眠', '评测口语'),
('孝喘', '哮喘', '评测口语'),
('藕吐', '呕吐', '评测口语'),
('头运', '头晕', '评测口语'),
('饿心', '恶心', '评测口语');

INSERT IGNORE INTO consult_uncertain_marker (marker, remark) VALUES
('不知道挂哪个科', '不确定定科'),
('不知道挂什么科', '不确定定科'),
('不知道挂哪科', '不确定定科'),
('不知道先看', '不确定定科'),
('分不清', '不确定定科'),
('说不清', '不确定定科');
