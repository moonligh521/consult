# -*- coding: utf-8 -*-
"""查今日医生排班；缺口则补近 7 天（与 DoctorSeed.fillWeek 一致）。不打印密码。"""
from __future__ import annotations

from datetime import date, timedelta
from pathlib import Path
import uuid

import pymysql

ROOT = Path(__file__).resolve().parents[2]


def load_mysql() -> dict:
    text = (ROOT / "config" / "application-local.yml").read_text(encoding="utf-8")
    cfg: dict = {}
    in_ds = False
    for line in text.splitlines():
        if "datasource:" in line:
            in_ds = True
            continue
        if in_ds and ("redis" in line or line.strip().startswith("data:")):
            if "redis" in line:
                break
        if not in_ds:
            continue
        stripped = line.strip()
        if stripped.startswith("url:") and "jdbc:mysql://" in stripped:
            raw = stripped.split("jdbc:mysql://", 1)[1]
            hostport, rest = raw.split("/", 1)
            host, port = hostport.split(":")
            cfg["host"] = host
            cfg["port"] = int(port)
            cfg["database"] = rest.split("?", 1)[0]
        elif stripped.startswith("username:"):
            cfg["user"] = stripped.split(":", 1)[1].strip().strip('"').strip("'")
        elif stripped.startswith("password:") and "password" not in cfg:
            cfg["password"] = stripped.split(":", 1)[1].strip().strip('"').strip("'")
    if not all(k in cfg for k in ("host", "port", "database", "user", "password")):
        raise SystemExit("application-local.yml 缺少 MySQL 连接")
    return cfg


def main() -> None:
    cfg = load_mysql()
    conn = pymysql.connect(
        host=cfg["host"],
        port=cfg["port"],
        user=cfg["user"],
        password=cfg["password"],
        database=cfg["database"],
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT CURDATE()")
            today = cur.fetchone()[0]
            print(f"库日期 {today}")

            cur.execute(
                """
                SELECT d.id, d.name, d.specialty, d.status, d.in_flight, d.concurrent_limit,
                       SUM(CASE WHEN s.visit_date = CURDATE() AND s.is_deleted = 0 AND s.stopped = 0
                                THEN 1 ELSE 0 END) AS today_slots,
                       SUM(CASE WHEN s.visit_date = CURDATE() AND s.is_deleted = 0 AND s.stopped = 0
                                THEN s.remain_num ELSE 0 END) AS today_remain
                FROM consult_doctor d
                LEFT JOIN consult_schedule s ON s.doctor_id = d.id
                WHERE d.is_deleted = 0
                GROUP BY d.id, d.name, d.specialty, d.status, d.in_flight, d.concurrent_limit
                ORDER BY d.name
                """
            )
            rows = cur.fetchall()
            print("医生|二级科|状态|在飞|上限|今日班次|今日余号")
            need_fill: list[str] = []
            for row in rows:
                doctor_id, name, specialty, status, in_flight, limit_n, slots, remain = row
                print(
                    f"{name}|{specialty}|{status}|{in_flight}|{limit_n}|{slots or 0}|{remain or 0}"
                )
                if status == 1 and (slots or 0) == 0:
                    need_fill.append(doctor_id)

            # 清演示占用，避免测试时 3/3 派不出
            cur.execute(
                """
                UPDATE consult_doctor
                SET in_flight = 0
                WHERE is_deleted = 0 AND name IN ('周宁','赵衡','孙悦','王启明','陈岚','苏敏','林晓','刘岩','韩霜','吴桐')
                """
            )
            cleared = cur.rowcount

            for doctor_id in need_fill:
                for i in range(7):
                    day = date.today() + timedelta(days=i)
                    for period, start, end, limit_num in (
                        ("上午", "08:00:00", "12:00:00", 20),
                        ("下午", "14:00:00", "17:30:00", 16),
                    ):
                        cur.execute(
                            """
                            SELECT id FROM consult_schedule
                            WHERE doctor_id = %s AND visit_date = %s AND period = %s AND is_deleted = 0
                            LIMIT 1
                            """,
                            (doctor_id, day, period),
                        )
                        if cur.fetchone():
                            continue
                        cur.execute(
                            """
                            INSERT INTO consult_schedule
                            (id, doctor_id, visit_date, period, start_time, end_time, visit_type,
                             limit_num, remain_num, stopped, is_deleted, created_at, updated_at)
                            VALUES (%s,%s,%s,%s,%s,%s,'图文',%s,%s,0,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))
                            """,
                            (
                                str(uuid.uuid4()),
                                doctor_id,
                                day,
                                period,
                                start,
                                end,
                                limit_num,
                                limit_num,
                            ),
                        )
                print(f"已补排班 doctor_id={doctor_id}")

            conn.commit()
            print(f"已清演示医生在飞 {cleared} 人")

            cur.execute(
                """
                SELECT d.name, s.period, s.remain_num, s.limit_num
                FROM consult_schedule s
                JOIN consult_doctor d ON d.id = s.doctor_id
                WHERE s.is_deleted = 0 AND s.stopped = 0 AND s.visit_date = CURDATE()
                ORDER BY d.name, s.period
                """
            )
            today_rows = cur.fetchall()
            print(f"今日有效排班 {len(today_rows)} 条")
            for name, period, remain, limit_num in today_rows:
                print(f"  {name} {period} 余{remain}/{limit_num}")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
