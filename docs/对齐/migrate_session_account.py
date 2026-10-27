# -*- coding: utf-8 -*-
"""给 consultation_session 补 account_id/title/transcript_json 和账号索引。"""

from __future__ import annotations

import re
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[2]
YML = ROOT / "config" / "application-local.yml"


def password() -> str:
    text = YML.read_text(encoding="utf-8")
    match = re.search(r'password:\s*"([^"]+)"', text)
    if not match:
        raise SystemExit("application-local.yml 没有 password")
    return match.group(1)


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
    alters = [
        (
            "account_id",
            "ALTER TABLE consultation_session ADD COLUMN account_id VARCHAR(64) NULL "
            "COMMENT '所属账号。登录后发第一句时写入' AFTER patient_name",
        ),
        (
            "title",
            "ALTER TABLE consultation_session ADD COLUMN title VARCHAR(64) NULL "
            "COMMENT '咨询记录列表标题' AFTER account_id",
        ),
        (
            "transcript_json",
            "ALTER TABLE consultation_session ADD COLUMN transcript_json MEDIUMTEXT NULL "
            "COMMENT '诊室对话快照JSON。分诊原话仍在 consultation_message' AFTER title",
        ),
    ]
    for column, ddl in alters:
        if column_exists(cur, "consultation_session", column):
            print("exists", column)
            continue
        cur.execute(ddl)
        print("added", column)
    if index_exists(cur, "consultation_session", "idx_consult_session_account"):
        print("exists idx_consult_session_account")
    else:
        cur.execute(
            "ALTER TABLE consultation_session ADD INDEX idx_consult_session_account (account_id, updated_at)"
        )
        print("added idx_consult_session_account")
    conn.commit()
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
