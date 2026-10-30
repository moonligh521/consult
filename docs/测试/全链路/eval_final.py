# -*- coding: utf-8 -*-
"""最终测评：各类型准确率 + 该不该走模型 + 失败原因（超时/JSON/不可用/识别错）。"""
from __future__ import annotations

import csv
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from coverage_model import _is_coverage_eligible  # noqa: E402
from gold_assert import evaluate  # noqa: E402

CSV_PATH = HERE / "triage-cases.csv"
FAIL_LOG = HERE / ".complaint-llm-fails.jsonl"
OUT_JSON = HERE / "_eval_final.json"

ROUTE_CERTAIN = {
    "SINGLE_SPECIALTY", "CANON_TERM", "PEDIATRIC", "OVERRIDE",
    "UNCERTAIN_MARKER", "SAME_L1_EARLIEST", "NO_HIT_SHORT",
}
ROUTE_FIXED = {"UNCERTAIN_MARKER", "OVERRIDE"}
CANON_UNCERTAIN = {"NO_HIT", "AMBIGUOUS_ONLY"}
SAFETY_STOP = {"URGENT", "NEED_HUMAN", "UNSUITABLE"}
MODEL_FAIL_REASONS = {
    "MODEL_TIMEOUT": "模型超时",
    "MODEL_INVALID_OUTPUT": "JSON/格式失败",
    "MODEL_UNAVAILABLE": "模型不可用",
    "MODEL_OUT_OF_CANDIDATES": "模型给科不在候选",
}


def pct(hit: int, n: int):
    return round(100.0 * hit / n, 2) if n else None


def should_call_model(row: dict) -> bool:
    rule = (row.get("expect_route_reason_rule") or "").strip()
    canon = (row.get("expect_canon_reason") or "").strip()
    if canon.startswith("MODEL_"):
        return True
    route_certain = rule in ROUTE_CERTAIN
    route_fixed = rule in ROUTE_FIXED
    canon_certain = canon not in CANON_UNCERTAIN
    return (not route_certain) or ((not canon_certain) and (not route_fixed))


def did_enter_model(row: dict) -> bool:
    reason = (row.get("expect_route_reason") or "").strip()
    source = (row.get("expect_route_source") or "").strip()
    canon_src = (row.get("expect_canon_source") or "").strip()
    if source == "MODEL" or canon_src == "MODEL":
        return True
    if reason.startswith("MODEL_"):
        return True
    return False


def fail_bucket(row: dict) -> str:
    reason = (row.get("expect_route_reason") or "").strip()
    source = (row.get("expect_route_source") or "").strip()
    spec = (row.get("expect_specialty") or "").strip()
    gold = (row.get("gold_specialty") or "").strip()
    if reason in MODEL_FAIL_REASONS:
        return MODEL_FAIL_REASONS[reason]
    if source == "MODEL" and spec and gold and spec != gold:
        return "模型识别错误（给了错科）"
    if source == "RULE" and spec and gold and spec != gold:
        return "规则挂科错误"
    if reason in {"NO_HIT", "NO_HIT_SHORT"}:
        return "规则未命中 " + reason
    if spec == "全科" and gold and gold != "全科":
        return "落到全科 reason=" + reason
    return f"其他 现网={spec} 金标={gold} {source}/{reason}"


def bump(store: dict, name: str, ok: bool) -> None:
    n, hit = store[name]
    store[name] = [n + 1, hit + (1 if ok else 0)]


def read_fail_log() -> Counter:
    counts: Counter = Counter()
    if not FAIL_LOG.exists():
        return counts
    with FAIL_LOG.open(encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                counts["LOG_CORRUPT"] += 1
                continue
            counts[row.get("status") or "?"] += 1
    return counts


def main() -> int:
    groups: dict[str, list[int]] = defaultdict(lambda: [0, 0])
    fail_reason: Counter = Counter()
    fail_samples: dict[str, list] = defaultdict(list)
    gate = Counter()
    gate_samples = {"该走却没走": [], "不该走却走了": []}
    safety = [0, 0]
    assertions: dict[str, list[int]] = defaultdict(lambda: [0, 0])
    rows_n = 0

    with CSV_PATH.open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            rows_n += 1
            node = row.get("node") or "(空)"
            suite = (row.get("suite") or "(空)").split("-")[0]
            fam = row.get("expression_family") or "(空)"
            lane = row.get("primary_lane") or "(空)"
            src = row.get("expect_route_source") or "(空)"
            reason = row.get("expect_route_reason") or "(空)"
            action = (row.get("action") or "MESSAGE").strip() or "MESSAGE"
            eligible = _is_coverage_eligible(row)
            token = row.get("spec_assert") or ""
            if token:
                ok = bool(evaluate(token, row))
                bump(assertions, "ALL", ok)
                bump(assertions, "node:" + node, ok)
                bump(assertions, "suite:" + suite, ok)

            if row.get("gold_safety_status") in SAFETY_STOP and eligible:
                safety[0] += 1
                hit = row.get("expect_safety_status") == row.get("gold_safety_status")
                if hit:
                    safety[1] += 1
                else:
                    fail_reason["安全金标不一致"] += 1

            if row.get("countable") == "1" and eligible:
                hit = row.get("gold_hit") == "1"
                for name in (
                    "ALL", "node:" + node, "lane:" + lane, "family:" + fam,
                    "source:" + src, "reason:" + reason, "suite:" + suite, "action:" + action,
                ):
                    bump(groups, name, hit)
                if not hit:
                    cat = fail_bucket(row)
                    fail_reason[cat] += 1
                    if len(fail_samples[cat]) < 8:
                        fail_samples[cat].append({
                            "suite": row.get("suite"),
                            "input": (row.get("input") or "")[:100],
                            "expect": row.get("expect_specialty"),
                            "gold": row.get("gold_specialty"),
                            "reason": reason,
                            "rule": row.get("expect_route_reason_rule"),
                            "source": src,
                        })

            safety_stopped = (row.get("expect_safety_status") or "") in SAFETY_STOP
            if action == "MESSAGE" and not safety_stopped:
                should = should_call_model(row)
                did = did_enter_model(row)
                if should and did:
                    gate["该走模型且进入了"] += 1
                    if reason in MODEL_FAIL_REASONS:
                        gate["进入后失败:" + MODEL_FAIL_REASONS[reason]] += 1
                    elif src == "MODEL":
                        gate["进入后模型成功出科"] += 1
                    else:
                        gate["进入后其他"] += 1
                elif should and not did:
                    gate["该走模型却没走"] += 1
                    if len(gate_samples["该走却没走"]) < 12:
                        gate_samples["该走却没走"].append({
                            "suite": row.get("suite"),
                            "input": (row.get("input") or "")[:100],
                            "rule": row.get("expect_route_reason_rule"),
                            "canon": row.get("expect_canon_reason"),
                            "final": reason,
                            "source": src,
                        })
                elif (not should) and did:
                    gate["不该走模型却走了"] += 1
                    if len(gate_samples["不该走却走了"]) < 12:
                        gate_samples["不该走却走了"].append({
                            "suite": row.get("suite"),
                            "input": (row.get("input") or "")[:100],
                            "rule": row.get("expect_route_reason_rule"),
                            "canon": row.get("expect_canon_reason"),
                            "final": reason,
                            "source": src,
                        })
                else:
                    gate["不该走且没走"] += 1

    live = read_fail_log()
    should_n = gate["该走模型且进入了"] + gate["该走模型却没走"]
    skip_n = gate["不该走且没走"] + gate["不该走模型却走了"]
    report = {
        "rows": rows_n,
        "safety": {"n": safety[0], "hit": safety[1], "pct": pct(safety[1], safety[0])},
        "assert": {k: {"n": v[0], "hit": v[1], "pct": pct(v[1], v[0])} for k, v in sorted(assertions.items())},
        "top1": {k: {"n": v[0], "hit": v[1], "pct": pct(v[1], v[0])} for k, v in sorted(groups.items())},
        "model_gate": {
            "该走模型": should_n,
            "不该走模型": skip_n,
            "该走识别准确率_pct": pct(gate["该走模型且进入了"], should_n),
            "不该走识别准确率_pct": pct(gate["不该走且没走"], skip_n),
            "counts": dict(gate),
            "samples": gate_samples,
        },
        "fail_reason": fail_reason.most_common(),
        "fail_samples": dict(fail_samples),
        "live_fail_log": dict(live),
    }
    OUT_JSON.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"读入 {rows_n} 行 → {OUT_JSON.name}", flush=True)
    print(f"二级科 Top1 {groups['ALL'][1]}/{groups['ALL'][0]} = {pct(groups['ALL'][1], groups['ALL'][0])}%", flush=True)
    print(f"规格断言 {assertions['ALL'][1]}/{assertions['ALL'][0]} = {pct(assertions['ALL'][1], assertions['ALL'][0])}%", flush=True)
    print(f"安全金标 {safety[1]}/{safety[0]} = {pct(safety[1], safety[0])}%", flush=True)
    print("该不该走模型：", dict(gate), flush=True)
    print(f"  该走 准确率 {pct(gate['该走模型且进入了'], should_n)}%  ({gate['该走模型且进入了']}/{should_n})", flush=True)
    print(f"  不该走 准确率 {pct(gate['不该走且没走'], skip_n)}%  ({gate['不该走且没走']}/{skip_n})", flush=True)
    print("失败原因：", flush=True)
    for name, count in fail_reason.most_common(15):
        print(f"  {count}\t{name}", flush=True)
    print("实呼失败日志：", dict(live), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
