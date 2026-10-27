# -*- coding: utf-8 -*-
"""六行门禁单。fail 退出码 1，pass 与 na 退出码 0。"""

from __future__ import annotations

import json
from pathlib import Path

LINES = ("behavior", "clinical", "quality", "reliability", "adversarial", "fault")


def write_gate(repo: Path, eval_id: str, rows: list[dict]) -> int:
    out_dir = repo / "target" / "eval"
    out_dir.mkdir(parents=True, exist_ok=True)
    payload = {"eval_id": eval_id, "rows": rows}
    (out_dir / "gate.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = [f"# 门禁单 {eval_id}", ""]
    failed = False
    for row in rows:
        lines.append(f"- {row['line']}: {row['result']} {row.get('detail', '')}".rstrip())
        if row["result"] == "fail":
            failed = True
    report = repo / "target" / "eval" / "report.md"
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 1 if failed else 0
