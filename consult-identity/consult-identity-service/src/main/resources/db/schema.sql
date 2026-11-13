-- 登录账号与就诊人。人工执行，不要 Flyway。可重复执行。

CREATE TABLE IF NOT EXISTS consult_account (
    id              VARCHAR(64)  NOT NULL COMMENT '账号编号',
    mobile          VARCHAR(16)  NOT NULL COMMENT '登录手机号',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    legacy_user_id  VARCHAR(64)  NULL     COMMENT '旧系统 dic_patient_user.user_id，迁移对账用',
    remark          VARCHAR(400) NULL     COMMENT '备注',
    created_by      VARCHAR(100) NULL     COMMENT '创建人',
    updated_by      VARCHAR(100) NULL     COMMENT '修改人',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted      BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除。0未删除；删除时写入非0，使手机号唯一键可复用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consult_account_mobile (mobile, is_deleted),
    KEY idx_consult_account_created (is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录账号。一行一个手机号，登录按手机号查';

CREATE TABLE IF NOT EXISTS consult_person (
    id                  VARCHAR(64)  NOT NULL COMMENT '就诊人档案编号',
    name                VARCHAR(32)  NOT NULL COMMENT '真实姓名',
    gender              TINYINT      NOT NULL COMMENT '性别。1男 2女',
    age                 INT          NOT NULL COMMENT '年龄，1到120',
    id_no               VARCHAR(32)  NULL     COMMENT '证件号。空表示未实名',
    birth_date          DATE         NULL     COMMENT '出生日期',
    legacy_patient_id   VARCHAR(64)  NULL     COMMENT '旧系统 pro_patient.patient_id，迁移对账用',
    remark              VARCHAR(400) NULL     COMMENT '备注',
    created_by          VARCHAR(100) NULL     COMMENT '创建人',
    updated_by          VARCHAR(100) NULL     COMMENT '修改人',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted          BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除。0未删除',
    PRIMARY KEY (id),
    KEY idx_consult_person_id_no (id_no, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='就诊人自然人档案。证件号可空，实名后按证件号查重';

CREATE TABLE IF NOT EXISTS consult_account_patient (
    account_id   VARCHAR(64)  NOT NULL COMMENT '登录账号',
    person_id    VARCHAR(64)  NOT NULL COMMENT '就诊人档案',
    relation     VARCHAR(16)  NULL     COMMENT '与账号持有人关系。本人/配偶/子女/父母/其他',
    is_default   TINYINT      NOT NULL DEFAULT 0 COMMENT '1默认就诊人',
    remark       VARCHAR(400) NULL     COMMENT '备注',
    created_by   VARCHAR(100) NULL     COMMENT '创建人',
    updated_by   VARCHAR(100) NULL     COMMENT '修改人',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted   BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除。0未删除；删除时写入非0，使同一绑定可再建',
    PRIMARY KEY (account_id, person_id, is_deleted),
    KEY idx_consult_account_patient_person (person_id, is_deleted),
    KEY idx_consult_account_patient_list (account_id, is_deleted, is_default, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号与就诊人绑定。列表按默认就诊人、创建时间排';

CREATE TABLE IF NOT EXISTS consult_staff (
    id              VARCHAR(64)  NOT NULL COMMENT '工作人员编号',
    username        VARCHAR(32)  NOT NULL COMMENT '登录名',
    password_hash   VARCHAR(100) NOT NULL COMMENT '密码哈希',
    name            VARCHAR(32)  NOT NULL COMMENT '显示名',
    doctor_id       VARCHAR(64)  NULL     COMMENT '绑定的医生。空则不能回复、不能心跳',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    remark          VARCHAR(400) NULL     COMMENT '备注',
    created_by      VARCHAR(100) NULL     COMMENT '创建人',
    updated_by      VARCHAR(100) NULL     COMMENT '修改人',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted      BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除。0未删除；删除时写入非0，使登录名可复用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consult_staff_username (username, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端工作人员。登录按用户名查';

CREATE TABLE IF NOT EXISTS consult_doctor (
    id                  VARCHAR(64)  NOT NULL COMMENT '医生编号',
    name                VARCHAR(32)  NOT NULL COMMENT '姓名',
    gender              TINYINT      NOT NULL DEFAULT 1 COMMENT '1男 2女',
    title               VARCHAR(32)  NOT NULL COMMENT '职称',
    hospital            VARCHAR(64)  NOT NULL COMMENT '执业医院',
    department_code     INT          NOT NULL COMMENT '一级科室码，对应咨询目录',
    specialty_id        BIGINT       NULL     COMMENT 'consult_department_specialty.id',
    specialty           VARCHAR(32)  NOT NULL COMMENT '二级科室',
    skills              VARCHAR(400) NULL     COMMENT '擅长。匹配用，顿号分隔',
    intro               VARCHAR(400) NULL     COMMENT '简介。不参与匹配',
    in_flight           INT          NOT NULL DEFAULT 0 COMMENT '图文在途单数。挂上即占',
    concurrent_limit    INT          NOT NULL DEFAULT 3 COMMENT '图文同时可接上限',
    status              TINYINT      NOT NULL DEFAULT 1 COMMENT '1出诊 0停诊',
    idle_since          DATETIME(3)  NULL     COMMENT '进入当前在手单数层的时间。公平队列用',
    last_beat           DATETIME(3)  NULL     COMMENT '最近一次心跳',
    presence            TINYINT      NOT NULL DEFAULT 0 COMMENT '1在线并接受派单 0下线',
    legacy_doc_id       VARCHAR(64)  NULL     COMMENT '旧系统 dic_doctor.doc_id，迁移对账用',
    remark              VARCHAR(400) NULL     COMMENT '备注',
    created_by          VARCHAR(100) NULL     COMMENT '创建人',
    updated_by          VARCHAR(100) NULL     COMMENT '修改人',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted          BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除。0未删除',
    PRIMARY KEY (id),
    KEY idx_consult_doctor_dept (department_code, status, is_deleted),
    KEY idx_consult_doctor_specialty (specialty, status, is_deleted),
    KEY idx_consult_doctor_presence (presence, last_beat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='医生档案。派单按二级科或一级科加出诊状态取人';

CREATE TABLE IF NOT EXISTS consult_schedule (
    id              VARCHAR(64)  NOT NULL COMMENT '排班编号',
    doctor_id       VARCHAR(64)  NOT NULL COMMENT '医生',
    visit_date      DATE         NOT NULL COMMENT '出诊日期',
    weekday         TINYINT      NOT NULL COMMENT '星期。1一 7日',
    period          VARCHAR(8)   NOT NULL COMMENT '上午/下午/晚上',
    start_time      CHAR(5)      NOT NULL COMMENT '开始 HH:mm',
    end_time        CHAR(5)      NOT NULL COMMENT '结束 HH:mm',
    visit_type      VARCHAR(16)  NOT NULL DEFAULT '图文' COMMENT '出诊类型。图文/视频',
    limit_num       INT          NOT NULL DEFAULT 20 COMMENT '限号',
    remain_num      INT          NOT NULL DEFAULT 20 COMMENT '剩余号源',
    stopped         TINYINT      NOT NULL DEFAULT 0 COMMENT '1临时停诊',
    legacy_vr_id    VARCHAR(64)  NULL     COMMENT '旧系统 pro_visit_real.vr_id，迁移对账用',
    remark          VARCHAR(400) NULL     COMMENT '备注',
    created_by      VARCHAR(100) NULL     COMMENT '创建人',
    updated_by      VARCHAR(100) NULL     COMMENT '修改人',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted      BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除。0未删除；删除时写入非0，使同一时段可再排',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consult_schedule_slot (doctor_id, visit_date, period, is_deleted),
    KEY idx_consult_schedule_day (visit_date, is_deleted, stopped, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='医生出诊排班。占坑只取当天未停诊且剩余号大于0的班';

CREATE TABLE IF NOT EXISTS consult_doctor_occupancy (
    order_id      VARCHAR(64)  NOT NULL COMMENT '订单号，幂等键',
    doctor_id     VARCHAR(64)  NOT NULL COMMENT '医生编号',
    schedule_id   VARCHAR(64)  NOT NULL COMMENT '排班编号',
    status        TINYINT      NOT NULL COMMENT '1占用中 2已释放-问诊结束 3已释放-取消退款 4已释放-超时',
    occupied_at   DATETIME(3)  NOT NULL COMMENT '占用时间',
    expires_at    DATETIME(3)  NOT NULL COMMENT '到期自动结束',
    released_at   DATETIME(3)  NULL     COMMENT '释放时间',
    remark        VARCHAR(400) NULL     COMMENT '备注',
    created_by    VARCHAR(100) NULL     COMMENT '创建人',
    updated_by    VARCHAR(100) NULL     COMMENT '修改人',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted    BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除。0未删除；删除时写入非0，使同一订单可再占',
    PRIMARY KEY (order_id, is_deleted),
    KEY idx_consult_doctor_occupancy_doctor (doctor_id, status, is_deleted),
    KEY idx_consult_doctor_occupancy_due (status, is_deleted, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='医生占坑。同一订单只占一次，超时扫描按到期时间取占用中的行';

CREATE TABLE IF NOT EXISTS consult_assignment_outbox (
    order_id         VARCHAR(64)  NOT NULL COMMENT '订单号，幂等键',
    consultation_id  VARCHAR(64)  NULL     COMMENT '咨询编号',
    doctor_id        VARCHAR(64)  NOT NULL COMMENT '派到的医生',
    specialty        VARCHAR(32)  NULL     COMMENT '二级科',
    schedule_id      VARCHAR(64)  NULL     COMMENT '排班',
    status           TINYINT      NOT NULL DEFAULT 0 COMMENT '0待发 1已发 2发送中',
    claim_token      VARCHAR(64)  NULL     COMMENT '本批领取令牌。两台补发不会领到同一行',
    claimed_at       DATETIME(3)  NULL     COMMENT '领走时间。超过 60 秒未发出则放回',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '写入时间',
    sent_at          DATETIME(3)  NULL     COMMENT '发送成功时间',
    PRIMARY KEY (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='派单通知。与占坑同一事务写入，发送失败留待重投';

CREATE TABLE IF NOT EXISTS consult_doctor_inbox (
    order_id         VARCHAR(64)  NOT NULL COMMENT '订单号，消费幂等键',
    consultation_id  VARCHAR(64)  NULL     COMMENT '咨询编号',
    doctor_id        VARCHAR(64)  NOT NULL COMMENT '医生',
    specialty        VARCHAR(32)  NULL     COMMENT '二级科',
    schedule_id      VARCHAR(64)  NULL     COMMENT '排班',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '入箱时间',
    PRIMARY KEY (order_id),
    KEY idx_consult_doctor_inbox_doctor (doctor_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='医生待接。同一订单只入箱一次';
