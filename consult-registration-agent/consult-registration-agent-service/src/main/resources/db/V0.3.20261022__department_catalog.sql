-- V0.3.20261022 科室目录。人工在目标库执行。服务启动不自动建表。
-- 匹配只认 enabled=1 的行。变更后必须走应用内延时双删，不要只改库不删 Redis。

CREATE TABLE IF NOT EXISTS consult_department (
    code           INT          NOT NULL COMMENT '对外科室码，卡片JSON存这个数字。启用后不要改号、不要复用',
    name           VARCHAR(32)  NOT NULL COMMENT '展示名',
    enabled        TINYINT      NOT NULL DEFAULT 1 COMMENT '1才可匹配、可手选',
    sort_no        INT          NOT NULL DEFAULT 0 COMMENT '下拉顺序',
    fallback       TINYINT      NOT NULL DEFAULT 0 COMMENT '全库只能有一行=1，对不上词时落全科',
    gender_scope   TINYINT      NOT NULL DEFAULT 0 COMMENT '0不限 1仅男 2仅女',
    child_age_lt   INT          NULL     COMMENT '小于该年龄走本科。儿科填14',
    remark         VARCHAR(400) NULL     COMMENT '备注',
    created_by     VARCHAR(100) NULL     COMMENT '创建人',
    updated_by     VARCHAR(100) NULL     COMMENT '修改人',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted     BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除',
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='已开通科室。匹配不能超出本表 enabled=1';

CREATE TABLE IF NOT EXISTS consult_department_keyword (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    department_code   INT          NOT NULL COMMENT '所属科室码',
    keyword           VARCHAR(32)  NOT NULL COMMENT '去空格后匹配用的词',
    kind              TINYINT      NOT NULL DEFAULT 1 COMMENT '1 ROUTING 先出现获胜 2 OVERRIDE 中医/儿童标记',
    remark            VARCHAR(400) NULL     COMMENT '备注',
    created_by        VARCHAR(100) NULL     COMMENT '创建人',
    updated_by        VARCHAR(100) NULL     COMMENT '修改人',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted        BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consult_department_keyword (department_code, keyword, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='科室匹配词。只能挂到已开通科室';
