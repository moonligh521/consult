-- 已有库补咨询记录列：账号、标题、诊室快照。可重复执行需先判断列是否存在。
ALTER TABLE consultation_session
    ADD COLUMN account_id VARCHAR(64) NULL COMMENT '所属账号。登录后发第一句时写入' AFTER patient_name;
ALTER TABLE consultation_session
    ADD COLUMN title VARCHAR(64) NULL COMMENT '咨询记录列表标题' AFTER account_id;
ALTER TABLE consultation_session
    ADD COLUMN transcript_json MEDIUMTEXT NULL COMMENT '诊室对话快照JSON。分诊原话仍在 consultation_message' AFTER title;
ALTER TABLE consultation_session
    ADD INDEX idx_consult_session_account (account_id, updated_at);
