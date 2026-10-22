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
    for stmt in statements(SEED.read_text(encoding="utf-8")):
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
