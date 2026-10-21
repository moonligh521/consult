CREATE TABLE consultation_session (
    id                          VARCHAR(64)  NOT NULL PRIMARY KEY,
    business_status             TINYINT      NOT NULL COMMENT '1 待描述 2 待补槽 4 卡片待确认 5 已确认',
    service_type                TINYINT      NULL     COMMENT '1 线下 2 互联网复诊 3 远程会诊',
    gender                      TINYINT      NULL,
    age                         INT          NULL,
    duration                    VARCHAR(64)  NULL,
    description                 TEXT         NOT NULL COMMENT '卡片描述，可编辑；不是原话',
    card_json                   JSON         NULL,
    urgent_latched              BOOLEAN      NOT NULL DEFAULT FALSE,
    prompt_evaluation_latched   BOOLEAN      NOT NULL DEFAULT FALSE,
    human_requested_latched     BOOLEAN      NOT NULL DEFAULT FALSE,
    unsuitable_latched          BOOLEAN      NOT NULL DEFAULT FALSE,
    undetermined                BOOLEAN      NOT NULL DEFAULT FALSE,
    fallback_reason             VARCHAR(32)  NULL,
    urgent_template_id          VARCHAR(64)  NULL,
    notice_template_id          VARCHAR(64)  NULL,
    last_message_seq            INT          NOT NULL DEFAULT 0,
    last_evaluated_seq          INT          NOT NULL DEFAULT 0,
    created_at                  DATETIME(3)  NOT NULL,
    updated_at                  DATETIME(3)  NOT NULL
);

CREATE TABLE consultation_message (
    consultation_id  VARCHAR(64)  NOT NULL,
    seq              INT          NOT NULL,
    message_id       VARCHAR(64)  NOT NULL,
    source           TINYINT      NOT NULL COMMENT '1 MESSAGE 2 CARD_EDIT',
    text             TEXT         NOT NULL,
    received_at      DATETIME(3)  NOT NULL,
    PRIMARY KEY (consultation_id, seq),
    UNIQUE KEY uk_consultation_message (consultation_id, message_id)
);

CREATE TABLE safety_decision_audit (
    decision_id           VARCHAR(64)  NOT NULL PRIMARY KEY,
    consultation_id       VARCHAR(64)  NOT NULL,
    message_seq           INT          NOT NULL,
    trigger_type          TINYINT      NOT NULL COMMENT '1 MESSAGE 2 CARD_EDIT 3 CONFIRM_RECHECK',
    evaluation            TINYINT      NOT NULL COMMENT '1 EVALUATED 2 PINNED_URGENT',
    matched_rule_ids      JSON         NOT NULL,
    shadow_rule_ids       JSON         NOT NULL,
    evidence_refs         JSON         NOT NULL COMMENT '[{messageSeq,start,end}]，不存原文',
    catalog_version       VARCHAR(32)  NOT NULL,
    rule_engine_version   VARCHAR(32)  NOT NULL,
    model_status          TINYINT      NOT NULL COMMENT '0 DISABLED 1 SKIPPED 2 SUCCESS 3 TIMEOUT 4 UNAVAILABLE 5 INVALID_OUTPUT',
    model_version         VARCHAR(64)  NULL,
    fallback_reason       VARCHAR(32)  NULL,
    previous_status       TINYINT      NOT NULL COMMENT '对外状态码',
    resulting_status      TINYINT      NOT NULL,
    business_before       TINYINT      NOT NULL,
    business_after        TINYINT      NOT NULL,
    template_id           VARCHAR(64)  NULL,
    decision_time         DATETIME(3)  NOT NULL,
    KEY idx_audit_consultation (consultation_id, message_seq)
);
