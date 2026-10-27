# -*- coding: utf-8 -*-
"""核对：开通二级科是否都有医生、今日是否都有排班。"""
from __future__ import annotations

import json
from pathlib import Path

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
        if in_ds and "redis" in line:
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
    return cfg


def seed_specialties() -> list[tuple[int, str, str]]:
    seed = json.loads(
        (ROOT / "consult-registration-agent/consult-registration-agent-service/src/main/resources/department/catalog-seed.json")
        .read_text(encoding="utf-8")
    )
    rows = []
    for dept in seed.get("departments") or []:
        if not dept.get("enabled", True):
            continue
        code = dept["code"]
        l1 = dept.get("name") or str(code)
        for spec in dept.get("specialties") or []:
            rows.append((code, l1, spec["name"]))
    return rows


def main() -> None:
    specs = seed_specialties()
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
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT d.department_code, d.specialty, d.name, d.status,
                       SUM(CASE WHEN s.visit_date = CURDATE() AND s.is_deleted = 0 AND s.stopped = 0
                                AND s.remain_num > 0 THEN 1 ELSE 0 END) AS today_open
                FROM consult_doctor d
                LEFT JOIN consult_schedule s ON s.doctor_id = d.id
                WHERE d.is_deleted = 0
                GROUP BY d.id, d.department_code, d.specialty, d.name, d.status
                ORDER BY d.department_code, d.specialty, d.name
                """
            )
            doctors = cur.fetchall()
    finally:
        conn.close()

    by_spec: dict[str, list] = {}
    for code, specialty, name, status, today_open in doctors:
        key = specialty or ""
        by_spec.setdefault(key, []).append(
            {
                "code": code,
                "name": name,
                "status": status,
                "today_open": int(today_open or 0),
            }
        )

    print(f"开通二级科 {len(specs)} 个；库内医生 {len(doctors)} 位")
    print("一级|二级|有医生|今日有号|医生")
    missing_doc = []
    missing_today = []
    for code, l1, specialty in specs:
        rows = by_spec.get(specialty, [])
        active = [r for r in rows if r["status"] == 1]
        with_slot = [r for r in active if r["today_open"] > 0]
        names = ",".join(f"{r['name']}(余班{r['today_open']})" for r in active) or "-"
        has_doc = "Y" if active else "N"
        has_today = "Y" if with_slot else "N"
        print(f"{l1}|{specialty}|{has_doc}|{has_today}|{names}")
        if not active:
            missing_doc.append(specialty)
        elif not with_slot:
            missing_today.append(specialty)

    # 医生挂了种子里没有的科
    seed_names = {s[2] for s in specs}
    extra = sorted(k for k in by_spec if k and k not in seed_names)
    if extra:
        print("医生二级科不在开通目录:", ", ".join(extra))
    if missing_doc:
        print("无医生的开通二级科:", ", ".join(missing_doc))
    if missing_today:
        print("有医生但今日无号的二级科:", ", ".join(missing_today))
    if not missing_doc and not missing_today:
        print("结论: 每个开通二级科都有在职医生，且今日有可派排班。")
    else:
        print("结论: 尚未做到每个开通二级科都有今日排班。")


if __name__ == "__main__":
    main()
