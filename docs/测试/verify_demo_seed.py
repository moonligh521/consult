# -*- coding: utf-8 -*-
from pathlib import Path
import pymysql

ROOT = Path(__file__).resolve().parents[2]


def load_mysql():
    text = (ROOT / "config" / "application-local.yml").read_text(encoding="utf-8")
    cfg = {}
    in_ds = False
    for line in text.splitlines():
        if "datasource:" in line:
            in_ds = True
            continue
        if in_ds and "redis" in line:
            break
        if not in_ds:
            continue
        s = line.strip()
        if s.startswith("url:") and "jdbc:mysql://" in s:
            raw = s.split("jdbc:mysql://", 1)[1]
            hp, rest = raw.split("/", 1)
            h, p = hp.split(":")
            cfg.update(host=h, port=int(p), database=rest.split("?", 1)[0])
        elif s.startswith("username:"):
            cfg["user"] = s.split(":", 1)[1].strip().strip('"').strip("'")
        elif s.startswith("password:") and "password" not in cfg:
            cfg["password"] = s.split(":", 1)[1].strip().strip('"').strip("'")
    return cfg


def main():
    cfg = load_mysql()
    conn = pymysql.connect(
        host=cfg["host"],
        port=cfg["port"],
        user=cfg["user"],
        password=cfg["password"],
        database=cfg["database"],
        charset="utf8mb4",
    )
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT id, mobile, status FROM consult_account WHERE mobile=%s AND is_deleted=0",
            ("15513981390",),
        )
        acc = cur.fetchone()
        print("account", acc)
        if acc:
            cur.execute(
                """
                SELECT p.name, p.gender, p.age, b.relation
                FROM consult_account_patient b
                JOIN consult_person p ON p.id = b.person_id
                WHERE b.account_id=%s AND b.is_deleted=0
                """,
                (acc[0],),
            )
            print("patients", cur.fetchall())
        cur.execute("SELECT COUNT(*) FROM consult_doctor WHERE is_deleted=0 AND status=1")
        print("doctors", cur.fetchone()[0])
        cur.execute(
            """
            SELECT COUNT(DISTINCT specialty) FROM consult_doctor d
            JOIN consult_schedule s ON s.doctor_id=d.id AND s.is_deleted=0 AND s.stopped=0
              AND s.visit_date=CURDATE() AND s.remain_num>0
            WHERE d.is_deleted=0 AND d.status=1
            """
        )
        print("specialties_with_today", cur.fetchone()[0])
        cur.execute(
            """
            SELECT specialty, COUNT(*) c FROM consult_doctor
            WHERE is_deleted=0 AND status=1
            GROUP BY specialty HAVING c < 2
            ORDER BY c, specialty LIMIT 20
            """
        )
        thin = cur.fetchall()
        print("specialties_under_2", thin)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
