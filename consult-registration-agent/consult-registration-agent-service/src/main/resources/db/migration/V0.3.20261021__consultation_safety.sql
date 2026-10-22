-- V0.3.20261021 咨询急症与安全分流建表。
-- 启动时 Flyway 自动执行；也可在目标库手工执行。所有枚举列存数字码。

CREATE TABLE consultation_session (
    id                          VARCHAR(64)  NOT NULL COMMENT '本轮咨询编号',
    business_status             TINYINT      NOT NULL COMMENT '业务状态。1待描述 2待补槽 4卡片待确认 5已确认。急症等对外状态不写这里',
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
    created_at                  DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at                  DATETIME(3)  NOT NULL COMMENT '最近更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='咨询会话。一行一轮，FOR UPDATE 锁这一行';

CREATE TABLE consultation_message (
    consultation_id  VARCHAR(64)  NOT NULL COMMENT '所属咨询编号',
    seq              INT          NOT NULL COMMENT '本轮原话序号，从1递增',
    message_id       VARCHAR(64)  NOT NULL COMMENT '幂等键。客户端传入或服务端生成',
    source           TINYINT      NOT NULL COMMENT '来源。1患者消息 2改卡描述',
    text             TEXT         NOT NULL COMMENT '原话正文。只追加，不修改、不删除',
    received_at      DATETIME(3)  NOT NULL COMMENT '收到这条原话的时间',
    PRIMARY KEY (consultation_id, seq),
    UNIQUE KEY uk_consultation_message (consultation_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='患者原话。只追加，不更新、不删除';

CREATE TABLE safety_decision_audit (
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
    previous_status       TINYINT      NOT NULL COMMENT '评估前对外状态码。3急症 6转人工 7降级 8渠道不适用等',
    resulting_status      TINYINT      NOT NULL COMMENT '评估后对外状态码',
    business_before       TINYINT      NOT NULL COMMENT '评估前业务状态码。1待描述 2待补槽 4卡片待确认 5已确认',
    business_after        TINYINT      NOT NULL COMMENT '评估后业务状态码',
    template_id           VARCHAR(64)  NULL     COMMENT '给患者看的提示模板编号',
    decision_time         DATETIME(3)  NOT NULL COMMENT '决策时间',
    PRIMARY KEY (decision_id),
    KEY idx_audit_consultation (consultation_id, message_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全决策审计。不含病情原文。只追加';
