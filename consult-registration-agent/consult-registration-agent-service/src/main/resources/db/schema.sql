-- 挂号核心表。人工执行，不要 Flyway。新库先跑本文件再跑 seed.sql。
-- 已有库可重复执行：CREATE IF NOT EXISTS 不会改已存在的表。
-- 二级科表若没有 department_code，先 DROP consult_department_specialty 再跑本文件。
-- 主诉归约改造前建的库：再执行一次 db/migrate-triage.sql 补列和初始数据。

CREATE TABLE IF NOT EXISTS consultation_session (
    id                          VARCHAR(64)  NOT NULL COMMENT '挂号咨询ID',
    business_status             TINYINT      NOT NULL COMMENT '业务状态。1待描述 2待补槽 4卡片待确认 5已确认',
    service_type                TINYINT      NULL     COMMENT '服务类型。1线下门诊挂号 2互联网复诊 3远程会诊。空表示未指定',
    gender                      TINYINT      NULL     COMMENT '性别。1男 2女。空表示还没抽到',
    age                         INT          NULL     COMMENT '年龄，1到120。空表示还没抽到',
    patient_id                  VARCHAR(64)  NULL     COMMENT '本轮选用的就诊人编号。空表示还没选',
    patient_name                VARCHAR(32)  NULL     COMMENT '就诊人姓名快照，展示用',
    account_id                  VARCHAR(64)  NULL     COMMENT '所属账号。登录后发第一句时写入',
    title                       VARCHAR(64)  NULL     COMMENT '咨询记录列表标题',
    transcript_json             MEDIUMTEXT   NULL     COMMENT '诊室对话快照JSON。分诊原话仍在 consultation_message',
    duration                    VARCHAR(64)  NULL     COMMENT '病程，例如半年、3天。空表示还没抽到',
    description                 TEXT         NOT NULL COMMENT '卡片展示用的病情描述，可编辑，不是原话',
    card_json                   JSON         NULL     COMMENT '已生成的咨询卡片JSON。未齐或已作废为空',
    triage_json                 JSON         NULL     COMMENT '最近一次分诊决策（归约词、挂科来源）。派医生只读这里',
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
    PRIMARY KEY (id),
    KEY idx_consult_session_account (account_id, updated_at)
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
    certainty             TINYINT      NOT NULL DEFAULT 1 COMMENT '1规则明确 2规则不明确交模型',
    matched_rule_ids      JSON         NOT NULL COMMENT '参与决策的规则编号JSON数组',
    shadow_rule_ids       JSON         NOT NULL COMMENT '只记影子、不影响患者的规则编号JSON数组',
    model_trigger_ids     JSON         NULL     COMMENT '命中的模型触发编号。未触发为空',
    evidence_refs         JSON         NOT NULL COMMENT '证据位置[{messageSeq,start,end}]，不存原文',
    catalog_version       VARCHAR(32)  NOT NULL COMMENT '当时用的急症目录版本',
    rule_engine_version   VARCHAR(32)  NOT NULL COMMENT '当时用的规则引擎版本',
    model_status          TINYINT      NOT NULL COMMENT '模型补漏结果。0关闭 1跳过 2成功 3超时 4不可用 5输出不合格 6繁忙 7熔断',
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
    doctor_id         VARCHAR(64)    NULL     COMMENT '派单医生',
    doctor_name       VARCHAR(32)    NULL     COMMENT '医生姓名快照',
    doctor_title      VARCHAR(32)    NULL     COMMENT '职称快照',
    hospital          VARCHAR(64)    NULL     COMMENT '医院快照',
    department_code   INT            NULL     COMMENT '一级科室码',
    specialty         VARCHAR(32)    NULL     COMMENT '二级科室',
    skills_hit        VARCHAR(64)    NULL     COMMENT '命中的擅长词',
    schedule_id       VARCHAR(64)    NULL     COMMENT '占用的排班',
    consult_minutes   INT            NULL     COMMENT '问诊时长分钟。5/10/15/20/30',
    ends_at           DATETIME(3)    NULL     COMMENT '问诊到期时间',
    ended_at          DATETIME(3)    NULL     COMMENT '实际结束时间。有值表示会话已结束',
    end_reason        VARCHAR(16)    NULL     COMMENT '结束原因。ENDED手动 TIMEOUT超时 CANCEL取消',
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
    term_type         TINYINT      NOT NULL DEFAULT 1 COMMENT '1症状 2病名 3科室名。病名参与主诉归约',
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

CREATE TABLE IF NOT EXISTS consult_patient (
    id           VARCHAR(64)  NOT NULL COMMENT '就诊人编号',
    account_id   VARCHAR(64)  NOT NULL COMMENT '所属账号。一个账号可绑多个就诊人',
    name         VARCHAR(32)  NOT NULL COMMENT '真实姓名',
    gender       TINYINT      NOT NULL COMMENT '性别。1男 2女',
    age          INT          NOT NULL COMMENT '年龄，1到120',
    relation     VARCHAR(16)  NULL     COMMENT '与账号持有人关系。本人/配偶/子女/父母/其他',
    is_default   TINYINT      NOT NULL DEFAULT 0 COMMENT '1表示该账号默认就诊人',
    remark       VARCHAR(400) NULL     COMMENT '备注',
    created_by   VARCHAR(100) NULL     COMMENT '创建人',
    updated_by   VARCHAR(100) NULL     COMMENT '修改人',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted   BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除',
    PRIMARY KEY (id),
    KEY idx_consult_patient_account (account_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号下的就诊人档案。选人、新建都走本表';

CREATE TABLE IF NOT EXISTS consult_safety_catalog_release (
    catalog_version VARCHAR(64)  NOT NULL COMMENT '目录版本，主键',
    checksum        CHAR(64)     NOT NULL COMMENT 'payload SHA-256 十六进制',
    payload         LONGTEXT     NOT NULL COMMENT '当时的 urgent-catalog.json 原文',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次写入时间',
    PRIMARY KEY (catalog_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='急症目录不可变发版行。禁止改历史包';

CREATE TABLE IF NOT EXISTS consult_ambiguous_symptom (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    phrase       VARCHAR(32)  NOT NULL COMMENT '歧义症状短语',
    remark       VARCHAR(400) NULL     COMMENT '备注',
    created_by   VARCHAR(100) NULL     COMMENT '创建人',
    updated_by   VARCHAR(100) NULL     COMMENT '修改人',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted   BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除；删除后写入非0唯一值',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consult_ambiguous_symptom (phrase, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多系统都可能出现的症状。只有这些词时挂科交给模型';

CREATE TABLE IF NOT EXISTS consult_canon_synonym (
    rule_id       VARCHAR(32)  NOT NULL COMMENT '规则编号，例如 SYN-HTN-001',
    target_term   VARCHAR(32)  NOT NULL COMMENT '归约到的标准病名，必须是目录病名',
    all_groups    JSON         NOT NULL COMMENT '[[a,b],[c]]：每组至少命中一个，所有组都命中才算',
    none_terms    JSON         NOT NULL COMMENT '出现任一即不命中',
    pattern       VARCHAR(128) NULL     COMMENT '可选正则，只允许指标加数字写法',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1DRAFT只记影子 2REVIEWED参与决策 3RETIRED',
    reviewer      VARCHAR(64)  NULL     COMMENT '临床审核人',
    reviewed_at   DATETIME(3)  NULL     COMMENT '审核时间',
    remark        VARCHAR(400) NULL     COMMENT '备注',
    created_by    VARCHAR(100) NULL     COMMENT '创建人',
    updated_by    VARCHAR(100) NULL     COMMENT '修改人',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted    BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除；删除后写入非0唯一值',
    PRIMARY KEY (rule_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='口语到标准病名的归约规则。只有 REVIEWED 参与挂科';

CREATE TABLE IF NOT EXISTS consult_match_decision (
    decision_id        VARCHAR(64)  NOT NULL COMMENT '本条编号',
    run_id             VARCHAR(64)  NOT NULL COMMENT '一次出卡/改卡/派单的批次，同批多节点共用',
    consultation_id    VARCHAR(64)  NOT NULL COMMENT '所属咨询编号',
    order_id           VARCHAR(64)  NULL     COMMENT '派单时的订单号',
    message_seq        INT          NOT NULL COMMENT '决策时最后一条原话序号，复原输入用',
    trigger_type       TINYINT      NOT NULL COMMENT '1出卡 2改描述 3患者改科 4绑定就诊人 5确认 6派医生 7含糊判断',
    node               TINYINT      NOT NULL COMMENT '2含糊 3槽位 4归约 5抽症状 6挂科 7卡片确认 8派医生 9占坑',
    certainty          TINYINT      NOT NULL COMMENT '1明确 2不明确',
    source             TINYINT      NOT NULL COMMENT '1规则 2模型 3兜底 4患者',
    reason_code        VARCHAR(32)  NOT NULL COMMENT '判定原因',
    output_json        JSON         NOT NULL COMMENT '本节点输出，不含原文',
    evidence_refs      JSON         NOT NULL COMMENT '证据位置[{start,end}]，相对当时的 description',
    shadow_ids         JSON         NULL     COMMENT '影子同义规则编号',
    model_status       TINYINT      NULL     COMMENT '本节点依赖的模型状态。未调用为空',
    model_cached       BOOLEAN      NULL     COMMENT '模型结果是否来自缓存',
    model_latency_ms   INT          NULL     COMMENT '模型耗时',
    latency_ms         INT          NOT NULL COMMENT '本节点耗时',
    catalog_version    VARCHAR(32)  NOT NULL COMMENT '科室目录版本',
    prompt_version     VARCHAR(32)  NULL     COMMENT '模型提示词版本',
    model_name         VARCHAR(64)  NULL     COMMENT '模型名',
    input_fingerprint  CHAR(64)     NOT NULL COMMENT 'sha256(紧凑原话|年龄段|性别)',
    decision_time      DATETIME(3)  NOT NULL COMMENT '决策时间',
    remark             VARCHAR(400) NULL     COMMENT '备注',
    created_by         VARCHAR(100) NULL     COMMENT '创建人',
    updated_by         VARCHAR(100) NULL     COMMENT '修改人',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted         BIGINT       NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除；删除后写入非0唯一值',
    PRIMARY KEY (decision_id),
    KEY idx_match_consultation (consultation_id),
    KEY idx_match_run (run_id),
    KEY idx_match_node_time (node, decision_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='挂号链路逐节点决策。不含病情原文';

CREATE TABLE IF NOT EXISTS consult_label_task (
    task_id            VARCHAR(64)   NOT NULL COMMENT '标注任务编号',
    run_id             VARCHAR(64)   NOT NULL COMMENT '被抽中的决策批次',
    consultation_id    VARCHAR(64)   NOT NULL COMMENT '咨询编号',
    stratum            VARCHAR(64)   NOT NULL COMMENT '分层键：n4来源|n6来源|n6原因',
    stratum_weight     DECIMAL(10,6) NOT NULL COMMENT '抽样当日该层在总体中的占比',
    status             TINYINT       NOT NULL DEFAULT 1 COMMENT '1待标 2已标 3作废',
    gold_urgent        TINYINT       NULL     COMMENT '临床期望处置：1继续 2急症 3转人工 4不适宜',
    gold_terms         JSON          NULL     COMMENT '临床期望病种，可空数组',
    gold_specialty     VARCHAR(32)   NULL     COMMENT '临床期望二级科',
    gold_skill_hit     TINYINT       NULL     COMMENT '派到的医生擅长是否对症：1是 0否 空不适用',
    first_error_node   TINYINT       NULL     COMMENT '首个出错节点，全对为空',
    labeler            VARCHAR(64)   NULL     COMMENT '标注人',
    labeled_at         DATETIME(3)   NULL     COMMENT '标注时间',
    remark             VARCHAR(400)  NULL     COMMENT '备注',
    created_by         VARCHAR(100)  NULL     COMMENT '创建人',
    updated_by         VARCHAR(100)  NULL     COMMENT '修改人',
    created_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted         BIGINT        NOT NULL DEFAULT 0 COMMENT '删除标记。0未删除；删除后写入非0唯一值',
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_label_run (run_id, is_deleted),
    KEY idx_label_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='线上分层抽样标注';
