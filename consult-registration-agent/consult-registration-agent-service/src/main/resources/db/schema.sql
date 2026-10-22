-- 挂号核心表。人工执行，不要 Flyway。新库先跑本文件再跑 seed.sql。
-- 已有库可重复执行：CREATE IF NOT EXISTS 不会改已存在的表。
-- 二级科表若没有 department_code，先 DROP consult_department_specialty 再跑本文件。

CREATE TABLE IF NOT EXISTS consultation_session (
    id                          VARCHAR(64)  NOT NULL COMMENT '挂号咨询ID',
    business_status             TINYINT      NOT NULL COMMENT '业务状态。1待描述 2待补槽 4卡片待确认 5已确认',
    service_type                TINYINT      NULL     COMMENT '服务类型。1线下门诊挂号 2互联网复诊 3远程会诊。空表示未指定',
    gender                      TINYINT      NULL     COMMENT '性别。1男 2女。空表示还没抽到',
    age                         INT          NULL     COMMENT '年龄，1到120。空表示还没抽到',
    duration                    VARCHAR(64)  NULL     COMMENT '病程，例如半年、3天。空表示还没抽到',
    description                 TEXT         NOT NULL COMMENT '卡片展示用的病情描述，可编辑，不是原话',
    card_json                   JSON         NULL     COMMENT '已生成的咨询卡片JSON。未齐或已作废为空',
    urgent_latched              BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '急症锁存。一旦为1本轮不再降级',
    prompt_evaluation_latched   BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '需尽快评估，不停止挂号',
    human_requested_latched     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '患者明确要求转人工',
    unsuitable_latched          BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '当前线上渠道不适用',
    undetermined                BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '安全判断说不清楚，走降级',
    fallback_reason             VARCHAR(32)  NULL     COMMENT '降级原因。MODEL_UNAVAILABLE/MODEL_INVALID_OUTPUT/RULE_ENGINE_ERROR。未降级为空',
    urgent_template_id          VARCHAR(64)  NULL     COMMENT '急症提示模板编号',
    notice_template_id          VARCHAR(64)  NULL     COMMENT '尽快评估提示模板编号',
    last_message_seq            INT          NOT NULL DEFAULT 0 COMMENT '最后一条原话序号',
    last_evaluated_seq          INT          NOT NULL DEFAULT 0 COMMENT '已经评估到的原话序号。小于 last_message_seq 就要补评',
    remark                      VARCHAR(400) NULL     COMMENT '备注',
    created_by                  VARCHAR(100) NULL     COMMENT '创建人',
    updated_by                  VARCHAR(100) NULL     COMMENT '修改人',
    created_at                  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at                  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted                  BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除；删除后写入非0唯一值，保证业务唯一键能再用',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='咨询会话。一行一轮，FOR UPDATE 锁这一行';

CREATE TABLE IF NOT EXISTS consultation_message (
    consultation_id  VARCHAR(64)  NOT NULL COMMENT '所属咨询编号',
    seq              INT          NOT NULL COMMENT '本轮原话序号，从1递增',
    message_id       VARCHAR(64)  NOT NULL COMMENT '幂等键。客户端传入或服务端生成',
    source           TINYINT      NOT NULL COMMENT '来源。1患者消息 2改卡描述',
    text             TEXT         NOT NULL COMMENT '原话正文。只追加，不修改',
    received_at      DATETIME(3)  NOT NULL COMMENT '收到这条原话的时间',
    remark           VARCHAR(400) NULL     COMMENT '备注',
    created_by       VARCHAR(100) NULL     COMMENT '创建人',
    updated_by       VARCHAR(100) NULL     COMMENT '修改人',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted       BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除；删除后写入非0唯一值，保证业务唯一键能再用',
    PRIMARY KEY (consultation_id, seq),
    UNIQUE KEY uk_consultation_message (consultation_id, message_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='患者原话。只追加。删除走逻辑删除';

CREATE TABLE IF NOT EXISTS safety_decision_audit (
    decision_id           VARCHAR(64)  NOT NULL COMMENT '本条审计编号',
    consultation_id       VARCHAR(64)  NOT NULL COMMENT '所属咨询编号',
    message_seq           INT          NOT NULL COMMENT '评估到哪一条原话',
    trigger_type          TINYINT      NOT NULL COMMENT '触发入口。1发消息 2改卡 3确认前补评',
    evaluation            TINYINT      NOT NULL COMMENT '评估类型。1真正评估 2已急症钉死不再跑引擎',
    matched_rule_ids      JSON         NOT NULL COMMENT '参与决策的规则编号JSON数组',
    shadow_rule_ids       JSON         NOT NULL COMMENT '只记影子、不影响患者的规则编号JSON数组',
    evidence_refs         JSON         NOT NULL COMMENT '证据位置[{messageSeq,start,end}]，不存原文',
    catalog_version       VARCHAR(32)  NOT NULL COMMENT '当时用的急症目录版本',
    rule_engine_version   VARCHAR(32)  NOT NULL COMMENT '当时用的规则引擎版本',
    model_status          TINYINT      NOT NULL COMMENT '模型补漏结果。0关闭 1跳过 2成功 3超时 4不可用 5输出不合格',
    model_version         VARCHAR(64)  NULL     COMMENT '模型名称。未调用为空',
    fallback_reason       VARCHAR(32)  NULL     COMMENT '降级原因。未降级为空',
    previous_status       TINYINT      NOT NULL COMMENT '评估前对外状态码',
    resulting_status      TINYINT      NOT NULL COMMENT '评估后对外状态码',
    business_before       TINYINT      NOT NULL COMMENT '评估前业务状态码',
    business_after        TINYINT      NOT NULL COMMENT '评估后业务状态码',
    template_id           VARCHAR(64)  NULL     COMMENT '给患者看的提示模板编号',
    decision_time         DATETIME(3)  NOT NULL COMMENT '决策时间',
    remark                VARCHAR(400) NULL     COMMENT '备注',
    created_by            VARCHAR(100) NULL     COMMENT '创建人',
    updated_by            VARCHAR(100) NULL     COMMENT '修改人',
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted            BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除；删除后写入非0唯一值，保证业务唯一键能再用',
    PRIMARY KEY (decision_id),
    KEY idx_audit_consultation (consultation_id, message_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全决策审计。不含病情原文';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='咨询下单。演示支付，不接真实渠道';

CREATE TABLE IF NOT EXISTS consult_department (
    code           INT          NOT NULL COMMENT '一级类编号。1–10原号，11–22后补。启用后不要改号',
    name           VARCHAR(32)  NOT NULL COMMENT '一级类名，分类用',
    enabled        TINYINT      NOT NULL DEFAULT 1 COMMENT '1才可匹配、可手选',
    sort_no        INT          NOT NULL DEFAULT 0 COMMENT '弹层一级顺序',
    fallback       TINYINT      NOT NULL DEFAULT 0 COMMENT '全库只能有一行=1，对不上词时落全科',
    gender_scope   TINYINT      NOT NULL DEFAULT 0 COMMENT '0不限 1仅男 2仅女',
    child_age_lt   INT          NULL     COMMENT '小于该年龄走儿科。儿科填14',
    treat_desc     VARCHAR(500) NULL     COMMENT '一级类下治疗描述汇总',
    remark         VARCHAR(400) NULL     COMMENT '备注',
    created_by     VARCHAR(100) NULL     COMMENT '创建人',
    updated_by     VARCHAR(100) NULL     COMMENT '修改人',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted     BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除',
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='一级类。咨询科室挂二级科';

CREATE TABLE IF NOT EXISTS consult_department_keyword (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    department_code   INT          NOT NULL COMMENT '所属一级类',
    keyword           VARCHAR(32)  NOT NULL COMMENT '去空格后匹配用的词',
    specialty_name    VARCHAR(32)  NULL     COMMENT '挂到的二级科。OVERRIDE 为空',
    kind              TINYINT      NOT NULL DEFAULT 1 COMMENT '1 ROUTING 先出现获胜 2 OVERRIDE 中医/儿童标记',
    remark            VARCHAR(400) NULL     COMMENT '备注',
    created_by        VARCHAR(100) NULL     COMMENT '创建人',
    updated_by        VARCHAR(100) NULL     COMMENT '修改人',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted        BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consult_department_keyword (department_code, keyword, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='匹配词。挂到二级挂号科';

CREATE TABLE IF NOT EXISTS consult_department_specialty (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    department_code   INT          NOT NULL COMMENT '所属一级类',
    name              VARCHAR(32)  NOT NULL COMMENT '二级科室名，咨询科室挂这个',
    treat_desc        VARCHAR(400) NOT NULL COMMENT '主要治疗',
    sort_no           INT          NOT NULL DEFAULT 0 COMMENT '弹层顺序',
    remark            VARCHAR(400) NULL     COMMENT '备注',
    created_by        VARCHAR(100) NULL     COMMENT '创建人',
    updated_by        VARCHAR(100) NULL     COMMENT '修改人',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted        BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consult_department_specialty (department_code, name, is_deleted),
    KEY idx_consult_department_specialty_dept (department_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='二级挂号科';

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

CREATE TABLE IF NOT EXISTS consult_safety_catalog_release (
    catalog_version VARCHAR(64)  NOT NULL COMMENT '目录版本，主键',
    checksum        CHAR(64)     NOT NULL COMMENT 'payload SHA-256 十六进制',
    payload         LONGTEXT     NOT NULL COMMENT '当时的 urgent-catalog.json 原文',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次写入时间',
    PRIMARY KEY (catalog_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='急症目录不可变发版行。禁止改历史包';
