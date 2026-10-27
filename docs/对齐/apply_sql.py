# -*- coding: utf-8 -*-
"""执行 schema.sql + seed.sql，重建科室目录。不碰咨询会话。密码只从本机 yml 读。"""

from __future__ import annotations

import re
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[2]
YML = ROOT / "config" / "application-local.yml"
DB = (
    ROOT
    / "consult-registration-agent"
    / "consult-registration-agent-service"
    / "src"
    / "main"
    / "resources"
    / "db"
)
SCHEMA = DB / "schema.sql"
SEED = DB / "seed.sql"
IDENTITY = (
    ROOT
    / "consult-identity"
    / "consult-identity-service"
    / "src"
    / "main"
    / "resources"
    / "db"
    / "schema.sql"
)


def password() -> str:
    text = YML.read_text(encoding="utf-8")
    match = re.search(r'password:\s*"([^"]+)"', text)
    if not match:
        raise SystemExit("application-local.yml 没有 password")
    return match.group(1)


def statements(sql: str) -> list[str]:
    out: list[str] = []
    buf: list[str] = []
    for line in sql.splitlines():
        trimmed = line.strip()
        if not trimmed or trimmed.startswith("--"):
            continue
        buf.append(line)
        if trimmed.endswith(";"):
            out.append("\n".join(buf))
            buf = []
    return out


def column_exists(cur, table: str, column: str) -> bool:
    cur.execute(
        """
        SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s AND COLUMN_NAME = %s
        """,
        (table, column),
    )
    return cur.fetchone()[0] > 0


def index_exists(cur, table: str, name: str) -> bool:
    cur.execute(
        """
        SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s AND INDEX_NAME = %s
        """,
        (table, name),
    )
    return cur.fetchone()[0] > 0


def evict_redis(pwd: str) -> None:
    try:
        import redis
    except ImportError:
        print("redis python 包不可用，请稍后 DEL consult:dept:catalog")
        return
    host_match = re.search(r"host:\s*(\S+)", YML.read_text(encoding="utf-8"))
    host = host_match.group(1) if host_match else "127.0.0.1"
    client = redis.Redis(host=host, port=6379, password=pwd, socket_timeout=5)
    deleted = client.delete("consult:dept:catalog")
    print("redis_del", int(deleted))


def main() -> None:
    pwd = password()
    conn = pymysql.connect(
        host="117.72.43.169",
        port=3306,
        user="root",
        password=pwd,
        database="consult_agent",
        charset="utf8mb4",
        autocommit=False,
    )
    cur = conn.cursor()
    if column_exists(cur, "consult_department_specialty", "id") and not column_exists(
            cur, "consult_department_specialty", "department_code"):
        cur.execute("DROP TABLE consult_department_specialty")
        print("dropped old specialty table")
    for stmt in statements(SCHEMA.read_text(encoding="utf-8")):
        cur.execute(stmt)
    if not column_exists(cur, "consult_department", "treat_desc"):
        cur.execute(
            "ALTER TABLE consult_department ADD COLUMN treat_desc VARCHAR(500) NULL "
            "COMMENT '一级类下治疗描述汇总' AFTER child_age_lt"
        )
    if not column_exists(cur, "consult_department_keyword", "specialty_name"):
        cur.execute(
            "ALTER TABLE consult_department_keyword ADD COLUMN specialty_name VARCHAR(32) NULL "
            "COMMENT '挂到的二级科' AFTER keyword"
        )
    if not column_exists(cur, "consultation_session", "patient_id"):
        cur.execute(
            "ALTER TABLE consultation_session ADD COLUMN patient_id VARCHAR(64) NULL "
            "COMMENT '本轮选用的就诊人编号' AFTER age"
        )
    if not column_exists(cur, "consultation_session", "patient_name"):
        cur.execute(
            "ALTER TABLE consultation_session ADD COLUMN patient_name VARCHAR(32) NULL "
            "COMMENT '就诊人姓名快照' AFTER patient_id"
        )
    if not column_exists(cur, "consultation_session", "account_id"):
        cur.execute(
            "ALTER TABLE consultation_session ADD COLUMN account_id VARCHAR(64) NULL "
            "COMMENT '所属账号。登录后发第一句时写入' AFTER patient_name"
        )
    if not column_exists(cur, "consultation_session", "title"):
        cur.execute(
            "ALTER TABLE consultation_session ADD COLUMN title VARCHAR(64) NULL "
            "COMMENT '咨询记录列表标题' AFTER account_id"
        )
    if not column_exists(cur, "consultation_session", "transcript_json"):
        cur.execute(
            "ALTER TABLE consultation_session ADD COLUMN transcript_json MEDIUMTEXT NULL "
            "COMMENT '诊室对话快照JSON。分诊原话仍在 consultation_message' AFTER title"
        )
    if not index_exists(cur, "consultation_session", "idx_consult_session_account"):
        cur.execute(
            "ALTER TABLE consultation_session ADD INDEX idx_consult_session_account (account_id, updated_at)"
        )
    if not column_exists(cur, "consult_doctor", "skills"):
        cur.execute(
            "ALTER TABLE consult_doctor ADD COLUMN skills VARCHAR(400) NULL "
            "COMMENT '擅长。匹配用，顿号分隔' AFTER specialty"
        )
    if not column_exists(cur, "consult_doctor", "in_flight"):
        cur.execute(
            "ALTER TABLE consult_doctor ADD COLUMN in_flight INT NOT NULL DEFAULT 0 "
            "COMMENT '图文在途单数。挂上即占' AFTER intro"
        )
    if not column_exists(cur, "consult_doctor", "concurrent_limit"):
        cur.execute(
            "ALTER TABLE consult_doctor ADD COLUMN concurrent_limit INT NOT NULL DEFAULT 3 "
            "COMMENT '图文同时可接上限' AFTER in_flight"
        )
    if column_exists(cur, "consult_doctor", "skills"):
        cur.execute(
            "UPDATE consult_doctor SET skills = intro "
            "WHERE is_deleted = 0 AND (skills IS NULL OR skills = '') "
            "AND intro IS NOT NULL AND intro <> ''"
        )
    if not column_exists(cur, "consult_order", "doctor_id"):
        cur.execute(
            "ALTER TABLE consult_order ADD COLUMN doctor_id VARCHAR(64) NULL "
            "COMMENT '派单医生' AFTER paid_at"
        )
    if not column_exists(cur, "consult_order", "doctor_name"):
        cur.execute(
            "ALTER TABLE consult_order ADD COLUMN doctor_name VARCHAR(32) NULL "
            "COMMENT '医生姓名快照' AFTER doctor_id"
        )
    if not column_exists(cur, "consult_order", "doctor_title"):
        cur.execute(
            "ALTER TABLE consult_order ADD COLUMN doctor_title VARCHAR(32) NULL "
            "COMMENT '职称快照' AFTER doctor_name"
        )
    if not column_exists(cur, "consult_order", "hospital"):
        cur.execute(
            "ALTER TABLE consult_order ADD COLUMN hospital VARCHAR(64) NULL "
            "COMMENT '医院快照' AFTER doctor_title"
        )
    if not column_exists(cur, "consult_order", "department_code"):
        cur.execute(
            "ALTER TABLE consult_order ADD COLUMN department_code INT NULL "
            "COMMENT '一级科室码' AFTER hospital"
        )
    if not column_exists(cur, "consult_order", "specialty"):
        cur.execute(
            "ALTER TABLE consult_order ADD COLUMN specialty VARCHAR(32) NULL "
            "COMMENT '二级科室' AFTER department_code"
        )
    if not column_exists(cur, "consult_order", "skills_hit"):
        cur.execute(
            "ALTER TABLE consult_order ADD COLUMN skills_hit VARCHAR(64) NULL "
            "COMMENT '命中的擅长词' AFTER specialty"
        )
    if not column_exists(cur, "consult_order", "schedule_id"):
        cur.execute(
            "ALTER TABLE consult_order ADD COLUMN schedule_id VARCHAR(64) NULL "
            "COMMENT '占用的排班' AFTER skills_hit"
        )
    if not column_exists(cur, "consult_order", "consult_minutes"):
        cur.execute(
            "ALTER TABLE consult_order ADD COLUMN consult_minutes INT NULL "
            "COMMENT '问诊时长分钟。5/10/15/20/30' AFTER schedule_id"
        )
    if not column_exists(cur, "consult_order", "ends_at"):
        cur.execute(
            "ALTER TABLE consult_order ADD COLUMN ends_at DATETIME(3) NULL "
            "COMMENT '问诊到期时间' AFTER consult_minutes"
        )
    if not column_exists(cur, "consult_order", "ended_at"):
        cur.execute(
            "ALTER TABLE consult_order ADD COLUMN ended_at DATETIME(3) NULL "
            "COMMENT '实际结束时间' AFTER ends_at"
        )
    for stmt in statements(SEED.read_text(encoding="utf-8")):
        cur.execute(stmt)
    for stmt in statements(IDENTITY.read_text(encoding="utf-8")):
        cur.execute(stmt)
    conn.commit()
    cur.execute("SELECT COUNT(*) FROM consult_department WHERE is_deleted=0")
    print("l1", cur.fetchone()[0])
    cur.execute("SELECT COUNT(*) FROM consult_department_specialty WHERE is_deleted=0")
    print("l2", cur.fetchone()[0])
    cur.execute(
        "SELECT COUNT(*) FROM consult_department_keyword WHERE is_deleted=0 AND specialty_name IS NOT NULL"
    )
    print("kw_l2", cur.fetchone()[0])
    cur.execute("SHOW TABLES")
    print("tables", [row[0] for row in cur.fetchall()])
    cur.close()
    conn.close()
    evict_redis(pwd)


if __name__ == "__main__":
    main()
