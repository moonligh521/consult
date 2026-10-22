# -*- coding: utf-8 -*-
"""非生产：打开模型补漏后，抽样评急症漏报会不会降、误报会不会抬头。

不改生产标签。DRAFT 别名即使被模型抽成信号也不算升 120（与 SafetyGate 一致）。

用法：
    python docs/测试/eval_urgent_model.py
    set DEEPSEEK_API_KEY=... && python docs/测试/eval_urgent_model.py

默认每组抽样 20 条。没有密钥时只出目录口径，不打模型。
"""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from pathlib import Path

import rule_replica as engine
import score_registration_cases as score

LEAK_LIMIT = 20
FALSE_LIMIT = 20


def load_key() -> str:
    env = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if env:
        return env
    roots = [
        Path(__file__).resolve().parents[2] / "config" / "application-local.yml",
        Path(__file__).resolve().parents[2] / "consult-registration-agent"
        / "consult-registration-agent-service" / "config" / "application-local.yml",
    ]
    for path in roots:
        if not path.exists():
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            if "api-key" in line and ":" in line:
                value = line.split(":", 1)[1].strip().strip('"').strip("'")
                if value and not value.startswith("${"):
                    return value
    return ""


def draft_aliases(rules: list[dict]) -> set[str]:
    out: set[str] = set()
    for rule in rules:
        if rule.get("status") != "DRAFT":
            continue
        for alias in rule.get("aliases") or []:
            out.add(engine.nfkc_compact(alias))
    return out


def baseline_signal_types(rules: list[dict]) -> list[str]:
    types: list[str] = []
    for rule in rules:
        if rule.get("status") == "DRAFT":
            continue
        if rule.get("disposition") != "URGENT":
            continue
        signal = rule.get("signal")
        if signal:
            types.append(str(signal))
    if types:
        return list(dict.fromkeys(types))
    catalog = json.loads(engine.CATALOG_JSON.read_text(encoding="utf-8"))
    for rule in catalog.get("rules") or []:
        if rule.get("status") == "DRAFT":
            continue
        if rule.get("disposition") != "URGENT":
            continue
        signal = rule.get("signal")
        if signal:
            types.append(str(signal))
    return list(dict.fromkeys(types))


def covered_by_draft(text: str, aliases: set[str]) -> bool:
    compact = engine.nfkc_compact(text)
    return any(alias and alias in compact for alias in aliases)


def call_model(text: str, key: str, allowed: list[str]) -> dict:
    system = (
        "只输出 JSON：{\"signals\":[{\"type\":\"\",\"messageSeq\":1,\"evidence\":\"\","
        "\"polarity\":\"positive\",\"temporal_status\":\"current\",\"uncertainty\":\"low\"}],"
        "\"unresolved_ambiguity\":false}。"
        "允许的 type：" + ",".join(allowed) + "。"
        "对不上这些 type 就 signals=[]。"
        "只转义目录别名对不上、但明显等于这些 type 的说法。"
        "口角歪斜、口唇发子、胸口痛、压着痛、胸口闷已在影子目录，不要输出。"
        "不得把普通咳嗽、胃痛、拉肚子、胃胀映射成 chest_pain 或 dyspnea。"
        "不得编造未出现的急症。evidence 必须是原话子串。"
    )
    payload = {
        "model": "deepseek-chat",
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": "age=None gender=None\n1:" + text},
        ],
    }
    request = urllib.request.Request(
        "https://api.deepseek.com/chat/completions",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": "Bearer " + key,
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=8) as response:
            body = json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
        return {"ok": False, "reason": type(error).__name__, "signals": []}
    content = (
        body.get("choices") or [{}]
    )[0].get("message", {}).get("content", "")
    start = content.find("{")
    end = content.rfind("}")
    if start < 0 or end <= start:
        return {"ok": False, "reason": "invalid", "signals": []}
    try:
        parsed = json.loads(content[start : end + 1])
    except json.JSONDecodeError:
        return {"ok": False, "reason": "invalid", "signals": []}
    signals = []
    for item in parsed.get("signals") or []:
        evidence = str(item.get("evidence") or "")
        type_name = str(item.get("type") or "")
        if type_name in allowed and evidence and evidence in text:
            signals.append(type_name)
    return {"ok": True, "reason": "ok", "signals": signals}


def sample(rows: list[dict], group: str, limit: int) -> list[dict]:
    picked: list[dict] = []
    for row in rows:
        if row.get("分组") != group:
            continue
        picked.append(row)
        if len(picked) >= limit:
            break
    return picked


def main() -> None:
    path, rows = score.load_csv()
    rules = engine.load_rules()
    drafts = draft_aliases(rules)
    allowed = baseline_signal_types(rules)
    leak = sample(rows, "急症-漏报", LEAK_LIMIT)
    false_pos = [row for row in rows if "急症误报" in (row.get("生产与临床差异") or "")][:FALSE_LIMIT]
    print(f"csv {path.name}")
    print(f"急症-漏报抽样 {len(leak)}  急症误报抽样 {len(false_pos)}")
    print(f"基线急症 type {allowed}")

    leak_draft = sum(1 for row in leak if covered_by_draft(row["话术"], drafts))
    print(f"漏报抽样中仍是 DRAFT 影子口径 {leak_draft}/{len(leak)}（设计上模型也不得升 120）")

    key = load_key()
    if not key:
        print("没有 DEEPSEEK_API_KEY，只完成口径拆分，不打模型。")
        print("结论：急症漏报里影子部分不能靠 Agent 清零；口语部分需要密钥才能量化。")
        return

    leak_lift = 0
    leak_fail = 0
    leak_blocked_draft = 0
    for row in leak:
        got = call_model(row["话术"], key, allowed)
        if not got["ok"]:
            leak_fail += 1
            continue
        if covered_by_draft(row["话术"], drafts):
            leak_blocked_draft += 1
            continue
        if got["signals"]:
            leak_lift += 1
    false_extra = 0
    false_fail = 0
    for row in false_pos:
        # 误报组规则已经 120，模型不得否掉；这里看模型会不会再给无关 type。
        got = call_model(row["话术"], key, allowed)
        if not got["ok"]:
            false_fail += 1
            continue
        if got["signals"]:
            false_extra += 1
    print(f"模型可用。漏报抽样：可升基线 {leak_lift}  影子拦截 {leak_blocked_draft}  调用失败 {leak_fail}")
    print(f"误报抽样：模型仍抽出急症信号 {false_extra}  调用失败 {false_fail}（规则急症不会被否掉）")
    print("口径：影子不得升 BASELINE；误报组保持 D-15。")


if __name__ == "__main__":
    main()
