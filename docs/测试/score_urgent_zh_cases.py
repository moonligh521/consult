# -*- coding: utf-8 -*-
"""对照 urgent-catalog.json 评测 urgent-zh-cases.csv，打印分组统计与不符条目。"""

from __future__ import annotations

import csv
import json
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "consult-registration-agent/consult-registration-agent-service/src/main/resources/safety/urgent-catalog.json"
CSV_PATH = Path(__file__).with_name("urgent-zh-cases.csv")
FALLBACK_CSV = Path(__file__).with_name("urgent-zh-cases.next.csv")

SPLITTERS = ["但是", "不过", "可是", "然而", "，", "。", "；", "！", "？", ",", ".", ";", "!", "?", "\n"]
DOUBLE_NEGATIONS = ["不是没有", "没有不", "并非没有"]
UNCERTAIN_WORDS = ["是不是", "会不会", "好像", "可能", "不确定", "说不清"]
NEGATIONS = ["没有", "并非", "不是", "否认", "没", "无", "不", "未"]
RESOLVED_WORDS = ["已经好了", "缓解了", "不疼了", "消失了", "现在没事"]


def nfkc_compact(text: str) -> str:
    nfkc = unicodedata.normalize("NFKC", text or "")
    return "".join(ch.lower() for ch in nfkc if not ch.isspace())


def split_sentences(text: str) -> list[str]:
    out: list[str] = []
    cursor = 0
    while cursor < len(text):
        nxt = len(text)
        skip = 0
        for splitter in SPLITTERS:
            found = text.find(splitter, cursor)
            if found >= 0 and found < nxt:
                nxt = found
                skip = len(splitter)
        if nxt > cursor:
            out.append(text[cursor:nxt])
        cursor = min(len(text), nxt + skip)
    return out or [text]


def contains_any(text: str, needles: list[str]) -> bool:
    return any(needle in text for needle in needles)


def preceding(text: str, start: int, chars: int) -> str:
    return text[max(0, start - chars) : start]


def alias_matches(sentence: str, aliases: list[str]) -> list[tuple[int, int, str]]:
    taken = [False] * len(sentence)
    ordered = sorted(aliases, key=len, reverse=True)
    hits: list[tuple[int, int, str]] = []
    for alias in ordered:
        needle = nfkc_compact(alias)
        if not needle:
            continue
        from_idx = 0
        while from_idx <= len(sentence) - len(needle):
            at = sentence.find(needle, from_idx)
            if at < 0:
                break
            span = range(at, at + len(needle))
            if not any(taken[i] for i in span):
                for i in span:
                    taken[i] = True
                hits.append((at, at + len(needle), alias))
            from_idx = at + 1
    return hits


def action_of(rule: dict, sentence: str, start: int) -> str:
    window = preceding(sentence, start, 6)
    if contains_any(sentence, DOUBLE_NEGATIONS) or contains_any(sentence, UNCERTAIN_WORDS):
        return rule["uncertaintyPolicy"]
    if contains_any(window, NEGATIONS):
        return rule["negatedPolicy"]
    if contains_any(sentence, RESOLVED_WORDS):
        return rule["resolvedPolicy"]
    return "ESCALATE"


def evaluate(text: str, rules: list[dict]) -> dict:
    normalized = nfkc_compact(text)
    sentences = split_sentences(normalized)
    matched: list[str] = []
    shadows: list[str] = []
    urgent = human = unsuitable = undetermined = False
    for rule in rules:
        shadow = rule["status"] == "DRAFT"
        for sentence in sentences:
            for start, _end, _alias in alias_matches(sentence, rule.get("aliases") or []):
                action = action_of(rule, sentence, start)
                if action == "SUPPRESS":
                    continue
                if shadow:
                    shadows.append(rule["ruleId"])
                    continue
                matched.append(rule["ruleId"])
                if action == "UNDETERMINED":
                    undetermined = True
                    continue
                disp = rule["disposition"]
                if disp == "URGENT":
                    urgent = True
                elif disp == "HUMAN":
                    human = True
                elif disp == "UNSUITABLE_FOR_ONLINE":
                    unsuitable = True
    if urgent:
        status = "急症(3)"
    elif human:
        status = "转人工(6)"
    elif undetermined:
        status = "安全降级(7)"
    elif unsuitable:
        status = "渠道不适用(8)"
    else:
        status = "非急症"
    return {
        "status": status,
        "urgent": urgent,
        "human": human,
        "matched": list(dict.fromkeys(matched)),
        "shadows": list(dict.fromkeys(shadows)),
    }


def expected_ok(row: dict, got: dict) -> list[str]:
    misses: list[str] = []
    expect_status = row["生产期望状态"]
    expect_120 = row["生产是否应120"]
    expect_shadow = (row.get("期望影子规则") or "").strip()
    if expect_120 == "是" and not got["urgent"]:
        misses.append("漏拦急症")
    if expect_120 == "否" and got["urgent"]:
        misses.append("误拦急症")
    if expect_status == "转人工(6)" and got["status"] != "转人工(6)":
        misses.append("漏转人工")
    if expect_status != "转人工(6)" and expect_status != "急症(3)" and got["status"] == "转人工(6)":
        misses.append("误转人工")
    if expect_shadow and expect_shadow not in got["shadows"]:
        misses.append("影子漏记")
    return misses


def load_csv() -> tuple[Path, list[dict]]:
    if len(sys.argv) > 1:
        path = Path(sys.argv[1])
    elif FALLBACK_CSV.exists() and (
        not CSV_PATH.exists() or FALLBACK_CSV.stat().st_mtime >= CSV_PATH.stat().st_mtime
    ):
        path = FALLBACK_CSV
    else:
        path = CSV_PATH
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return path, list(csv.DictReader(handle))


def main() -> None:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    rules = catalog["rules"]
    path, rows = load_csv()
    group_counts: Counter[str] = Counter()
    topic_counts: Counter[str] = Counter()
    status_counts: Counter[str] = Counter()
    human_alias_hits: Counter[str] = Counter()
    miss_counts: Counter[str] = Counter()
    group_ok: dict[str, list[int]] = defaultdict(lambda: [0, 0])
    miss_samples: dict[str, list[str]] = defaultdict(list)

    human_aliases = next(r["aliases"] for r in rules if r["ruleId"] == "HUMAN-BASE-001")
    human_group_by_alias: Counter[str] = Counter()

    for row in rows:
        group_counts[row["分组"]] += 1
        topic_counts[row["临床主题"]] += 1
        status_counts[row["生产期望状态"]] += 1
        got = evaluate(row["话术"], rules)
        misses = expected_ok(row, got)
        group_ok[row["分组"]][1] += 1
        if misses:
            miss_counts.update(misses)
            group_ok[row["分组"]][0] += 0
            for kind in misses:
                if len(miss_samples[kind]) < 8:
                    miss_samples[kind].append(f"{row['编号']} {row['话术']} -> {got['status']} shadows={got['shadows']}")
        else:
            group_ok[row["分组"]][0] += 1
        if row["分组"] == "转人工":
            sentence = nfkc_compact(row["话术"])
            hit = ""
            for alias in sorted(human_aliases, key=len, reverse=True):
                if nfkc_compact(alias) in sentence:
                    hit = alias
                    break
            human_alias_hits[hit or "(未命中别名)"] += 1
            core_hit = hit or "(未命中别名)"
            human_group_by_alias[core_hit] += 1

    total = len(rows)
    matched = total - sum(1 for row in rows if expected_ok(row, evaluate(row["话术"], rules)))
    # recount matched without double evaluate: use group_ok
    matched = sum(ok for ok, _n in group_ok.values())
    print(f"catalog {catalog.get('catalogVersion')}  csv {path.name}  共 {total} 条")
    print()
    print("== 分组 ==")
    for group, n in group_counts.most_common():
        ok, alln = group_ok[group]
        rate = 100.0 * ok / alln if alln else 0
        print(f"  {group:12s} {n:5d}  符合 {ok:5d}  {rate:6.1f}%")
    print()
    print("== 生产期望状态 ==")
    for status, n in status_counts.most_common():
        print(f"  {status:12s} {n:5d}")
    print()
    print("== 转人工别名命中（仅「转人工」分组）==")
    for alias, n in human_group_by_alias.most_common():
        print(f"  {alias:12s} {n:5d}")
    print()
    print(f"总符合 {matched}/{total}  ({100.0 * matched / total:.1f}%)")
    if miss_counts:
        print("== 不符 ==")
        for kind, n in miss_counts.most_common():
            print(f"  {kind}: {n}")
            for sample in miss_samples[kind]:
                print(f"    - {sample}")
    else:
        print("全部符合 CSV 期望（生产急症/转人工/影子）。")
    leak_n = group_counts.get("已知漏报", 0)
    shadow_n = group_counts.get("影子观察", 0)
    print()
    print("== 口径提醒 ==")
    print(f"  已知漏报 {leak_n} 条：生产不应 120，符合=当前目录仍覆盖不到（不是失败）")
    print(f"  影子观察 {shadow_n} 条：生产不 120，但应记 DRAFT 影子")


if __name__ == "__main__":
    main()
