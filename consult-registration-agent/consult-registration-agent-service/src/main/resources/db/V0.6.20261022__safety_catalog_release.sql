-- V0.6.20261022 急症目录发版对照。人工在目标库执行。
-- 只允许 INSERT IGNORE，禁止 UPDATE。运行时仍读 classpath JSON，本表只留当时哪一包。

CREATE TABLE IF NOT EXISTS consult_safety_catalog_release (
    catalog_version VARCHAR(64)  NOT NULL COMMENT '目录版本，主键',
    checksum        CHAR(64)     NOT NULL COMMENT 'payload SHA-256 十六进制',
    payload         LONGTEXT     NOT NULL COMMENT '当时的 urgent-catalog.json 原文',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次写入时间',
    PRIMARY KEY (catalog_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='急症目录不可变发版行。禁止改历史包';
