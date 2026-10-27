# -*- coding: utf-8 -*-
"""质量线：合成反例在这里判；真实会话的硬禁读 Java 写出的 quality.json。"""

from __future__ import annotations

import json
from pathlib import Path

from hard_bans import violations

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
JAVA_JSON = REPO / "consult-registration-agent" / "consult-registration-agent-service" / "target" / "eval" / "quality.json"


def _fixture(remark: str) -> dict:
    raw = remark.split("expect_fail", 1)[1].strip()
    return json.loads(raw)


def quality() -> dict:
    import csv

    fails = []
    live = 0
    with (HERE / "quality-cases.csv").open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            remark = row["remark"]
            if remark.startswith("expect_fail"):
                found = violations(row["kind"], _fixture(remark))
                if not found:
                    fails.append(f"{row['case_id']} 反例没有命中硬禁")
            elif remark == "live":
                live += 1
    if not JAVA_JSON.exists():
        return {"line": "quality", "result": "fail", "detail": "没有 Java 质量结果，先跑 HardBanEvalTest"}
    produced = json.loads(JAVA_JSON.read_text(encoding="utf-8"))
    if len(produced) != live:
        fails.append(f"Java 质量条数 {len(produced)} 与 live 行 {live} 不一致")
    for item in produced:
        if not item.get("pass"):
            fails.append(f"{item.get('case_id')} {item.get('detail')}")
            continue
        found = violations(item["kind"], item)
        if found:
            fails.append(f"{item.get('case_id')} " + "；".join(found))
    if fails:
        return {"line": "quality", "result": "fail", "detail": "；".join(fails[:8])}
    return {"line": "quality", "result": "pass", "detail": ""}
