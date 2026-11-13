-- 日均 10 万单扛量：已有库手工执行一次。新库 schema.sql 已含下面的列和索引，报已存在可忽略。
-- 千万行以上的表加索引请走 pt-online-schema-change / gh-ost，或在低峰执行（MySQL 8 默认 INPLACE，不锁写）。

-- 管理端咨询列表按时间窗倒序，不再全表 filesort。
ALTER TABLE consultation_session ADD KEY idx_consult_session_updated (updated_at);

-- 停科影响面按二级科计数：card_json 里的 specialty 抽成虚拟生成列，不占存储，只建索引。
ALTER TABLE consultation_session
    ADD COLUMN card_specialty VARCHAR(32) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(card_json, '$.specialty'))) VIRTUAL
        COMMENT '卡片二级科，生成列。停科影响面按它走索引';
ALTER TABLE consultation_session ADD KEY idx_consult_session_card (business_status, card_specialty);

-- 导诊漏斗按 decision_time 范围聚合，原 (node, decision_time) 用不上范围。
ALTER TABLE consult_match_decision ADD KEY idx_match_time (decision_time);
