# -*- coding: utf-8 -*-
"""行为线：不变量由 Maven 另跑；这里收两份回放的退出结果。临床未签核则记 na。"""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]


def _run(script: Path, cwd: Path) -> tuple[int, str]:
    env = os.environ.copy()
    env["PYTHONIOENCODING"] = "utf-8"
    env["PYTHONUTF8"] = "1"
    proc = subprocess.run(
        [sys.executable, str(script)],
        cwd=cwd,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        env=env,
    )
    return proc.returncode, (proc.stdout or "") + (proc.stderr or "")


def behavior() -> dict:
    triage_code, triage_out = _run(REPO / "docs" / "测试" / "全链路" / "score_triage_cases.py", REPO / "docs" / "测试" / "全链路")
    urgent_code, urgent_out = _run(REPO / "docs" / "测试" / "score_urgent_zh_cases.py", REPO / "docs" / "测试")
    gap = REPO / "target" / "eval" / "clinical-gap.md"
    gap.parent.mkdir(parents=True, exist_ok=True)
    gap.write_text(
        "临床列未签核，下面只是差距，不参与门禁。\n"
        "10 科口语集 registration-cases.csv 已退出行为线，改看全链路 triage-cases.csv。\n\n"
        + urgent_out[-2000:],
        encoding="utf-8",
    )
    problems = []
    if triage_code != 0:
        problems.append(f"全链路回放退出码 {triage_code}")
    if urgent_code != 0:
        problems.append(f"急症中文集退出码 {urgent_code}")
    if problems:
        return {"line": "behavior", "result": "fail", "detail": "；".join(problems)}
    return {"line": "behavior", "result": "pass", "detail": ""}


def clinical() -> dict:
    signed = HERE / "clinical-signed.json"
    if not signed.exists():
        return {"line": "clinical", "result": "na", "detail": "临床列未签核，只出差距"}
    return {"line": "clinical", "result": "fail", "detail": "已有签核文件，但自动判率尚未接入，不能当成通过"}
