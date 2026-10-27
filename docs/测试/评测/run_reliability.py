# -*- coding: utf-8 -*-
"""可靠线。样本文件不存在时记不适用。--live 只允许打评测环境，不打正式环境。"""

from __future__ import annotations

from pathlib import Path

HERE = Path(__file__).resolve().parent


def reliability(live: bool) -> dict:
    csv_path = HERE / "reliability-cases.csv"
    if not live:
        return {"line": "reliability", "result": "na", "detail": "未加 --live"}
    if not csv_path.exists():
        return {"line": "reliability", "result": "na", "detail": "可靠样本尚未冻结"}
    n = sum(1 for _ in csv_path.open(encoding="utf-8")) - 1
    return {"line": "reliability", "result": "na", "detail": f"样本已冻结 {n} 条，运行器尚未接入三次重跑"}
