ALTER TABLE consult_department_specialty
    ADD COLUMN audience TINYINT NOT NULL DEFAULT 2
    COMMENT '0不限 1仅儿童 2仅成人' AFTER enabled;

UPDATE consult_department_specialty
SET audience = CASE
    WHEN name = '全科' THEN 0
    WHEN name = '儿科'
      OR name = '新生儿科'
      OR name = '中医儿科'
      OR name LIKE '小儿%'
      OR name LIKE '新生儿%' THEN 1
    ELSE 2
END
WHERE is_deleted = 0;
