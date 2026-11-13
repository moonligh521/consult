-- 日均 10 万单扛量：已有库手工执行一次。新库 schema.sql 已含，报已存在可忽略。

-- 派单候选按二级科取，不再拉全院排班。
ALTER TABLE consult_doctor ADD KEY idx_consult_doctor_specialty (specialty, is_deleted);
