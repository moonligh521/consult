-- 医生在线、心跳、空闲时间。已有库手工执行一次。新库 schema.sql 已含，报已存在可忽略。

ALTER TABLE consult_doctor
    ADD COLUMN idle_since DATETIME(3) NULL COMMENT '进入当前在手单数层的时间。公平队列用' AFTER status,
    ADD COLUMN last_beat DATETIME(3) NULL COMMENT '最近一次心跳' AFTER idle_since,
    ADD COLUMN presence TINYINT NOT NULL DEFAULT 0 COMMENT '1在线并接受派单 0下线' AFTER last_beat;

ALTER TABLE consult_doctor ADD KEY idx_consult_doctor_presence (presence, last_beat);
