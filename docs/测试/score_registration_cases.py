# -*- coding: utf-8 -*-
"""评测 registration-cases.csv：先自检复刻结果，再出回归通过率和生产-临床差距。

用法：
    python docs/测试/score_registration_cases.py
    python docs/测试/score_registration_cases.py 其他.csv
"""

from __future__ import annotations

import csv
import sys
from collections import Counter, defaultdict
from pathlib import Path

import rule_replica as engine

CSV_PATH = Path(__file__).with_name("registration-cases.csv")
FALLBACK_CSV = Path(__file__).with_name("registration-cases.next.csv")

# DepartmentRouterTest.java 的黄金样例，用来验证复刻没跑偏。
ROUTER_GOLDEN = [
    ("干咳半年", "呼吸内科", "干咳"),
    ("头痛两天", "神经内科", "头痛"),
    ("胃痛三天", "消化内科", "胃痛"),
    ("手腕骨折两天", "创伤骨科", "骨折"),
    ("体检报告看不懂", "全科", None),
    ("挂皮肤科", "皮肤科", "皮肤科"),
    ("挂眼科", "眼科", "眼科"),
    ("喉咙痒，老想清嗓子", "普内科", "喉咙痒"),
    ("足跟疼", "足踝外科", "足跟疼"),
    ("甲沟炎", "普外科", "甲沟炎"),
    ("头通两天", "神经内科", "头痛"),
    ("刻嗽三天", "呼吸内科", "咳嗽"),
    ("肚子疼，说不清哪个位置", "全科", None),
    ("咳嗽还头痛", "呼吸内科", "咳嗽"),
]


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


def self_check() -> list[str]:
    """用仓库自己的断言验证复刻：目录正/反例 + 科室路由黄金样例。"""
    problems: list[str] = []
    rules = engine.load_rules()
    for rule in rules:
        for case in rule.get("positiveCases") or []:
            got = engine.evaluate(case, rules)
            if rule["status"] == "DRAFT":
                if rule["ruleId"] not in got["shadows"]:
                    problems.append(f"{rule['ruleId']} 正例未记影子: {case}")
            elif rule["disposition"] == "URGENT" and not got["urgent"]:
                problems.append(f"{rule['ruleId']} 正例未升急症: {case}")
            elif rule["disposition"] == "HUMAN" and got["status"] != engine.STATUS_HUMAN:
                problems.append(f"{rule['ruleId']} 正例未转人工: {case}")
        for case in rule.get("negativeCases") or []:
            got = engine.evaluate(case, rules)
            if got["urgent"] and rule["disposition"] == "URGENT":
                problems.append(f"{rule['ruleId']} 反例误升急症: {case}")
            if rule["disposition"] == "HUMAN" and got["status"] == engine.STATUS_HUMAN:
                problems.append(f"{rule['ruleId']} 反例误转人工: {case}")
    for text, dept, evidence in ROUTER_GOLDEN:
        got_dept, got_evidence = engine.advise(text)
        if got_dept != dept or got_evidence != evidence:
            problems.append(f"科室路由 {text} 期望 {dept}/{evidence} 实得 {got_dept}/{got_evidence}")
    return problems


def main() -> None:
    rules = engine.load_rules()
    path, rows = load_csv()
    total = len(rows)

    group = Counter()
    scene = Counter()
    status = Counter()
    dept = Counter()
    diff = Counter()
    shadow = Counter()
    mismatches: list[str] = []
    dept_pairs: Counter[tuple[str, str]] = Counter()
    diff_samples: dict[str, list[str]] = defaultdict(list)

    for row in rows:
        group[row["分组"]] += 1
        scene[row["场景"]] += 1
        status[row["生产期望状态"]] += 1
        dept[row["生产期望科室"]] += 1
        if row["期望影子规则"]:
            for rule_id in row["期望影子规则"].split(","):
                shadow[rule_id] += 1
        for item in (row["生产与临床差异"] or "").split("/"):
            if item:
                diff[item] += 1
                if len(diff_samples[item]) < 5:
                    diff_samples[item].append(f"    {row['编号']} 「{row['话术']}」 生产={row['生产期望状态']}/{row['生产期望科室']} 临床={row['临床期望急症']}/{row['临床期望科室']}")

        # 回归：CSV 里的生产期望必须和当前代码复刻结果一致。
        got = engine.evaluate(row["话术"], rules)
        expect_status = row["生产期望状态"]
        expect_urgent = row["生产是否应120"] == "是"
        expect_dept = row["生产期望科室"]
        expect_shadow = row["期望影子规则"]
        bad: list[str] = []
        if got["status"] != expect_status:
            bad.append(f"状态 {expect_status}->{got['status']}")
        if got["urgent"] != expect_urgent:
            bad.append(f"120 {expect_urgent}->{got['urgent']}")
        if expect_dept.startswith("（"):
            if got["status"] == engine.STATUS_PLAIN:
                bad.append("应不出卡却出卡")
        elif got["department"] != expect_dept:
            bad.append(f"科室 {expect_dept}->{got['department']}")
        if expect_shadow and expect_shadow != ",".join(got["shadows"]):
            bad.append(f"影子 {expect_shadow}->{got['shadows']}")
        if bad:
            if len(mismatches) < 20:
                mismatches.append(f"    {row['编号']} 「{row['话术']}」 " + "；".join(bad))
        if got["status"] == engine.STATUS_PLAIN:
            dept_pairs[(row["临床期望科室"], got["department"])] += 1

    problems = self_check()
    print(f"csv {path.name}  共 {total} 条  目录版本 {engine.catalog_version()}")
    print()
    print("== 自检（目录自带正/反例 + DepartmentRouterTest 黄金样例）==")
    if problems:
        for line in problems[:20]:
            print("  !! " + line)
        print(f"  共 {len(problems)} 处，复刻与仓库断言不一致，先修 rule_replica.py")
    else:
        print("  全部通过：复刻结果与仓库自带断言一致")
    print()
    print("== 分组 ==")
    for name, n in group.most_common():
        print(f"  {name:14s} {n:5d}  {100.0 * n / total:5.1f}%")
    print()
    print("== 场景（口语化维度）==")
    for name, n in scene.most_common():
        print(f"  {name:16s} {n:5d}")
    print()
    print("== 生产期望状态 ==")
    for name, n in status.most_common():
        print(f"  {name:12s} {n:5d}  {100.0 * n / total:5.1f}%")
    print()
    print("== 生产期望科室 ==")
    for name, n in dept.most_common():
        print(f"  {name:12s} {n:5d}  {100.0 * n / total:5.1f}%")
    print()
    print("== 影子规则命中 ==")
    for name, n in shadow.most_common():
        print(f"  {name:22s} {n:5d}")
    print()
    print("== 回归：CSV 期望 vs 当前代码复刻 ==")
    if mismatches:
        for line in mismatches:
            print(line)
        print(f"  共 {len(mismatches)} 条不符（这里不符说明代码行为变了，需要人工确认是有意改动还是回归缺陷）")
    else:
        print(f"  {total}/{total} 一致")
    print()
    print("== 生产 vs 临床 差距（按条数）==")
    for name, n in diff.most_common():
        print(f"  {name:12s} {n:5d}  {100.0 * n / total:5.1f}%")
        for sample in diff_samples[name]:
            print(sample)
    print()
    print("== 科室混淆（临床期望 x 生产实际，仅统计出卡条目）==")
    for (clin, prod), n in sorted(dept_pairs.items(), key=lambda kv: -kv[1]):
        flag = "" if clin == prod else "  <-- 不一致"
        print(f"  临床 {clin:8s} 生产 {prod:8s} {n:5d}{flag}")


if __name__ == "__main__":
    main()
