# -*- coding: utf-8 -*-
"""评测挂号全链路测试数据集：自检 → 重放 → 覆盖率 → 指标。

跑法：
    python docs/测试/全链路/score_triage_cases.py
    python docs/测试/全链路/score_triage_cases.py 其他.csv

三段输出：
1. 自检：Java 单测里的黄金样例（DepartmentRouterTest / DepartmentRouterRouteTest）+ 规格里的必过样本。
   这段不过，说明复刻器和代码跑偏了，后面结论不可信。
2. 重放：CSV 里的期望 vs 当前复刻器结果。出现不符 = 目录或规则改了，需要更新已提交的 CSV。
3. 覆盖 + 指标：137 个二级科是否都覆盖到、边界/负向/安全是否齐全、二级科 Top1 / 一级科 Top1。
"""
from __future__ import annotations

import csv
import json
import pathlib
import sys
from collections import Counter, defaultdict
from typing import Dict, List, Optional

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from gold_assert import STATUS_CODE, evaluate  # noqa: E402
from clinical_gold import (  # noqa: E402
    agreement_report,
    eligible_gold_rows,
    validate_clinical_gold,
)
from coverage_model import (  # noqa: E402
    _is_coverage_eligible,
    coverage_report,
    enrich_coverage_fields,
    load_taxonomy,
)
from release_gates import evaluate_release_gates  # noqa: E402
from rule_replica_v2 import Replica, URGENT_JSON, load_safety  # noqa: E402
from slang import LEXICON_VERSION  # noqa: E402

CSV_PATH = HERE / "triage-cases.csv"

# 1) 自检：Java 单测事实 + 规格里必须成立的点
# (输入, gender, age, 期望二级科, 期望轮首规则原因码)；None 表示不校验
# 说明：route() 才是 N6 卡片口径。无病名时也会 firstRouting（含一级科名）。
#       DepartmentRouterTest 里仍有 advise() 样例，这里只校二级科；
#       DepartmentRouterRouteTest 的样例是 route()，连原因码一起校。
SELF_CHECK_ROUTE = [
    ("高血压，头晕三天", 1, 50, "普内科", "CANON_TERM"),
    ("咳嗽三天", 1, 30, "呼吸内科", "SINGLE_SPECIALTY"),
    ("头痛，恶心，两天", 1, 30, "神经内科", None),
    ("头晕乏力一周", 1, 30, None, "AMBIGUOUS_ONLY"),
    ("不知道挂哪个科，头晕", 1, 30, None, "UNCERTAIN_MARKER"),
    ("想看中医调理失眠", 2, 40, None, "OVERRIDE"),
    ("胃痛，牙疼，三天", 1, 30, None, "CONFLICT"),
    ("孩子口渴，喝水多，尿多", 1, 5, None, "PEDIATRIC"),
    # 同一级不同二级 → SAME_L1_EARLIEST，先出现的那个二级赢
    ("咳嗽还头痛", None, None, "呼吸内科", "SAME_L1_EARLIEST"),
    ("头痛加咳嗽", None, None, "神经内科", "SAME_L1_EARLIEST"),
    ("腹 泻 ， 咳 嗽", None, None, "消化内科", "SAME_L1_EARLIEST"),
    # advise 老口径：只校二级科
    ("干咳半年", None, None, "呼吸内科", None),
    ("头痛两天", None, None, "神经内科", None),
    ("胃痛三天", None, None, "消化内科", None),
    ("手腕骨折两天", None, None, "创伤骨科", None),
    ("手上长湿疹", None, None, "皮肤科", None),
    ("眼睛红，有分泌物", None, None, "眼科", None),
    ("挂皮肤科", None, None, "皮肤科", None),
    ("挂眼科", None, None, "眼科", None),
    ("拉肚子三天", None, None, "消化内科", None),
    ("脑袋疼两天", None, None, "神经内科", None),
    ("打喷嚏流鼻涕", None, None, "耳鼻咽喉科", None),
    ("睡觉打呼噜", None, None, "耳鼻咽喉科", None),
    ("牙疼三天", None, None, "口腔科", None),
    ("阑尾炎复查", None, None, "胃肠外科", None),
    ("前列腺复查", None, None, "男科", None),
    ("月经不调", None, None, "妇科", None),
    ("头通两天", None, None, "神经内科", None),
    ("刻嗽三天", None, None, "呼吸内科", None),
    ("咳 嗽", None, None, "呼吸内科", None),
    ("孩子咳嗽三天", None, None, "小儿呼吸科", "PEDIATRIC"),
    ("咳嗽三天，13岁", None, None, "小儿呼吸科", "PEDIATRIC"),
    ("咳嗽三天，14岁", None, None, "呼吸内科", None),
    ("足跟疼", None, None, "足踝外科", None),
    ("甲沟炎", None, None, "普外科", None),
    ("腰肌劳损", None, None, "骨科", None),
    ("痔疮出血", None, None, "肛肠外科", None),
    ("鼻炎三天", None, None, "耳鼻咽喉科", None),
    ("体检报告看不懂", None, None, "全科", None),
    ("肚子疼，说不清哪个位置", None, None, "全科", "UNCERTAIN_MARKER"),
    ("挂内科", 2, 32, "普内科", "SINGLE_SPECIALTY"),
    ("挂外科", None, None, "普外科", "SINGLE_SPECIALTY"),
    ("挂皮肤性病科", 2, 32, "皮肤科", "SINGLE_SPECIALTY"),
    ("慢性病调药三天", 2, 32, "普内科", None),
    ("种植牙三天", 2, 32, "种植科", None),
    ("理疗三天", 2, 32, "理疗科", None),
    ("放疗后复查三天", 2, 55, "放疗科", None),
    ("肩周炎想针灸三天", 2, 32, "针灸科", "OVERRIDE"),
    ("孩子口腔溃疡三天", 2, 8, "小儿口腔科", "PEDIATRIC"),
    ("窝沟封闭三天", 2, 8, "口腔预防科", "PEDIATRIC"),
    ("小儿斜视三天", 2, 6, "小儿眼科", "PEDIATRIC"),
    ("孩子骨折康复三天", 2, 8, "小儿康复科", "PEDIATRIC"),
    ("手足口病三天", 2, 32, "全科", "NO_HIT_SHORT"),
    ("失眠", 2, 32, None, "AMBIGUOUS_ONLY"),
    ("除了头晕，主要是小儿疳积三天", 2, 32, "中医儿科", "PEDIATRIC"),
    ("头疼三天", 2, 32, "神经内科", None),
    ("打鼾三天", 2, 32, "耳鼻咽喉科", None),
    ("白内障三天", 2, 55, "白内障科", None),
    ("便血三天", 2, 32, "肛肠外科", None),
    ("kesou三天", 2, 32, "全科", "NO_HIT"),
    ("乳癖三天", 2, 32, "中医乳腺外科", "SINGLE_SPECIALTY"),
    ("小儿疳积三天", 2, 5, "中医儿科", "PEDIATRIC"),
    ("良性肌瘤三天", 2, 32, "肿瘤外科", None),
    ("不孕不育症三天", 2, 32, "计划生育科", None),
    ("挂中医按摩科三天", 2, 32, "中医按摩科", "OVERRIDE"),
]

SELF_CHECK_SAFETY = [
    ("胸痛两小时", 3), ("呼吸困难", 3), ("意识障碍", 3), ("口唇发紫", 3),
    ("转人工", 6), ("找人工", 6), ("人工客服", 6), ("有人工吗", 6), ("客服在哪", 6),
    ("快不行了", 6), ("疼得受不了", 6), ("感觉要死了", 6), ("快没气了", 6),
    ("干咳半年", 0), ("不要转人工，干咳半年", 0), ("人工智能导诊，干咳半年", 0),
    ("没有胸痛", 3),  # D-15 已发布行为：否定包不住 URGENT BASELINE
    ("口唇发子", 0), ("喘不上气", 0), ("胸口痛", 0),
    ("转人工不是转人", 0),
]

SELF_CHECK_STATUS = [
    ("女，32岁，干咳半年", None, None, 4),
    ("女，32岁，干咳", None, None, 2),
    ("女，干咳三天", None, None, 2),
    ("干咳三天", None, None, 2),
    ("有点不舒服", None, None, 1),
    ("胸痛两小时", None, None, 3),
    ("转人工", None, None, 6),
]

REPLAY_FIELDS = [
    ("expect_status_code", lambda r: str(r["status_code"])),
    ("expect_missing_slot", lambda r: r["slots"]["missing"] or ""),
    ("expect_safety_status", lambda r: {3: "URGENT", 6: "NEED_HUMAN", 7: "SAFE_FALLBACK",
                                        8: "UNSUITABLE"}.get(r["safety"]["status_code"], "PASS")),
    ("expect_department", lambda r: r["route"]["department"] or ""),
    ("expect_specialty", lambda r: r["route"]["specialty"] or ""),
    ("expect_route_reason", lambda r: r["route"]["reason"]),
    ("expect_route_reason_rule", lambda r: r["route_rule_first_pass"]["reason"]),
    ("expect_route_source", lambda r: r["route"]["source"]),
    ("expect_route_certainty", lambda r: r["route"]["certainty"]),
]

SKIP_REPLAY_SUITES = {"S13-确认-N7", "S14-派医生-N8", "S15-占坑-N9", "S29-多轮-确认取消"}
NON_REPLAY_ACTIONS = {
    "CONFIRM", "PATCH", "DISPATCH", "ORDER", "CANCEL",
    "MESSAGE_AFTER_CONFIRM", "MESSAGE_AFTER_ENDED", "MESSAGE_AFTER_CARD",
}
# 确认/改卡/派单/下单/出卡后再说话：产品状态机，不把当前句当 MESSAGE 重放。


def replayable(row: dict) -> bool:
    action = (row.get("action") or "MESSAGE").strip() or "MESSAGE"
    if action in NON_REPLAY_ACTIONS:
        return False
    if row.get("suite") in SKIP_REPLAY_SUITES:
        return False
    if row.get("expect_error_code"):
        return False
    return action == "MESSAGE"


SELF_CHECK_MULTI = [
    # (当前句, prior, turns, 期望状态, 期望二级科或 None, 缺槽或 "")
    ("女", ["咳嗽"], 2, 2, None, "AGE"),
    ("32岁", ["咳嗽", "女"], 3, 2, None, "DURATION"),
    ("三天", ["咳嗽", "女", "32岁"], 4, 4, "呼吸内科", ""),
    ("干咳三天", ["有点不舒服"], 2, 2, None, "GENDER"),
    ("其实是干咳", ["胸痛两小时"], 2, 3, None, ""),
]


def run_self_check(replica: Replica) -> bool:
    print("=== 1. 自检（Java 单测 + 规格必过样本）===")
    failures: List[str] = []

    for text, gender, age, spec, reason in SELF_CHECK_ROUTE:
        row_snapshots = replica.run(text, gender=gender, age=age)
        route_info = row_snapshots["route"]
        if spec and route_info["specialty"] != spec:
            failures.append(f"[N6 科室] 「{text}」期望 {spec}，实际 {route_info['specialty']}")
        if reason and row_snapshots["route_rule_first_pass"]["reason"] != reason:
            failures.append(f"[N6 原因] 「{text}」期望 {reason}，"
                            f"实际 {row_snapshots['route_rule_first_pass']['reason']}"
                            f"（融合后 {route_info['reason']}）")

    for text, expect in SELF_CHECK_SAFETY:
        view = replica.safety.evaluate([text])
        got = view.status_code or 0
        if got != expect:
            failures.append(f"[N1] 「{text}」期望停止码 {expect}，实际 {got}")

    for text, gender, age, status in SELF_CHECK_STATUS:
        got = replica.run(text, gender=gender, age=age)["status_code"]
        if got != status:
            failures.append(f"[状态] 「{text}」期望 {status}，实际 {got}")

    for text, prior, turns, status, spec, missing in SELF_CHECK_MULTI:
        got = replica.run(text, turns=turns, prior_messages=prior)
        if got["status_code"] != status:
            failures.append(f"[多轮状态] prior={prior} 「{text}」期望 {status}，实际 {got['status_code']}")
        if spec and got["route"]["specialty"] != spec:
            failures.append(f"[多轮科室] prior={prior} 「{text}」期望 {spec}，实际 {got['route']['specialty']}")
        if missing != (got["slots"]["missing"] or "") and status != 3:
            failures.append(f"[多轮槽位] prior={prior} 「{text}」期望缺 {missing}，实际 {got['slots']['missing']}")

    checked = len(SELF_CHECK_ROUTE) + len(SELF_CHECK_SAFETY) + len(SELF_CHECK_STATUS) + len(SELF_CHECK_MULTI)
    if failures:
        print(f"自检不过：{len(failures)}/{checked}")
        for line in failures[:40]:
            print("  " + line)
        return False
    print(f"自检通过：{checked}/{checked}")
    return True


def load_rows(path: pathlib.Path) -> List[dict]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def replay(rows: List[dict]) -> Dict[str, Counter]:
    print("\n=== 2. 重放（期望 vs 当前复刻器）===")
    replica = Replica()
    enforce_replica = Replica(enforce_draft=True, snapshot=replica.snapshot,
                              safety=load_safety(enforce_draft=True))
    ortho_off_replica = Replica(enforce_draft=False, snapshot=replica.snapshot.without_enabled(5),
                                safety=load_safety())
    reviewed_ids = ["SYN-HTN-001", "SYN-HTN-002", "SYN-DM-001", "SYN-DM-002",
                    "SYN-GOUT-001", "SYN-LIP-001"]
    reviewed_replica = Replica(enforce_draft=False,
                               snapshot=replica.snapshot.with_synonyms_reviewed(reviewed_ids),
                               safety=load_safety())
    gender_cn = {"男": 1, "女": 2}
    diffs = Counter()
    samples: Dict[str, str] = {}
    skipped = 0
    for row in rows:
        suite = row["suite"]
        if not replayable(row):
            skipped += 1
            continue
        engine = replica
        if suite.startswith("S16"):
            engine = ortho_off_replica
        elif row["scenario"] == "SYN-REVIEWED":
            engine = reviewed_replica
        elif row["scenario"] == "ENFORCE" or row["case_ref"].endswith("@ENFORCE"):
            engine = enforce_replica
        prior = [item for item in row["prior_input"].split("|") if item]
        session_gender = gender_cn.get(row.get("session_gender", ""))
        session_age = int(row["session_age"]) if row.get("session_age") else None
        result = engine.run(row["input"], turns=int(row["turn"] or 1), prior_messages=prior,
                            gender=session_gender, age=session_age)
        for column, extract in REPLAY_FIELDS:
            got = extract(result)
            want = row[column]
            if got != want:
                diffs[f"{suite}|{column}"] += 1
                samples.setdefault(f"{suite}|{column}",
                                   f"{row['case_id']} 「{row['input']}」 CSV={want} 实际={got}")
    print(f"重放 {len(rows) - skipped} 行（MESSAGE + prior_input），跳过 {skipped} 行（确认/改卡/派单/下单/出卡后发言）")
    if diffs:
        print("期望与复刻器不一致（目录或规则改了，需要重跑生成）：")
        for key, count in diffs.most_common(20):
            print(f"  {key}: {count}  例：{samples[key]}")
    else:
        print("全部一致 ✅")
    return diffs


def coverage(rows: List[dict], replica: Replica) -> None:
    print("\n=== 3. 覆盖率 ===")
    specialties = set(replica.snapshot.enabled_specialty_names())
    covered = {r["gold_specialty"] for r in rows if r["gold_specialty"]}
    missing = sorted(specialties - covered)
    print(f"二级科 {len(specialties)} 个，金标集合覆盖 {len(covered & specialties)} 个"
          + (f"，缺：{missing}" if missing else "，全覆盖 ✅"))

    refs = defaultdict(set)
    for row in rows:
        refs[row["suite"]].add(row["case_ref"].split("-")[0])
    for suite in sorted(refs):
        print(f"  {suite:<20} 用例号 {len(refs[suite])} 种")

    urgent_rules = {rule.rule_id for rule in load_safety().rules}
    hit_rules = set()
    for row in rows:
        hit_rules.update(v for v in row["expect_safety_rules"].split("|") if v)
        hit_rules.update(v for v in row["expect_shadow_rules"].split("|") if v)
    print(f"安全规则 {len(urgent_rules)} 条，数据集命中 {len(hit_rules & urgent_rules)} 条"
          + (f"，缺：{sorted(urgent_rules - hit_rules)}" if urgent_rules - hit_rules else "，全覆盖 ✅"))

    expected_refs = {f"B{i}" for i in range(1, 21)} | {f"NB{i}" for i in range(1, 16)} \
        | {f"N{i}" for i in range(1, 21)}
    present = {r["case_ref"] for r in rows}
    missing_refs = sorted(expected_refs - present)
    print("边界/负向用例号齐全" if not missing_refs else f"缺用例号：{missing_refs}")

    broken_prior = sum(1 for r in rows
                       if replayable(r) and int(r.get("turn") or 1) > 1 and not r.get("prior_input"))
    print("多轮 MESSAGE 均带 prior_input ✅" if broken_prior == 0
          else f"⚠ {broken_prior} 条多轮 MESSAGE 缺 prior_input")
    actions = Counter((r.get("action") or "MESSAGE") for r in rows)
    print("action 分布：" + "，".join(f"{k} {v}" for k, v in actions.most_common()))

    report = coverage_report(rows)
    print("\n去重覆盖口径：")
    print(f"  原始行 {report['raw_rows']}，有效覆盖行 {report['eligible_rows']}，"
          f"唯一医学意图 {report['unique_intents']}，唯一场景 {report['unique_scenarios']}，"
          f"有效样本 {report['effective_samples']}")
    print(f"  唯一多轮轨迹 {report['unique_multiturn_trajectories']}，"
          f"最大重复簇 {report['max_duplicate_cluster_size']} 行"
          f"（占有效行 {report['max_duplicate_cluster_share']:.2%}）")
    print("  lane 行数：" + "，".join(
        f"{name} {count}" for name, count in sorted(report["lane_counts"].items())
    ))
    print("  lane 配额完成：" + "，".join(
        f"{name} {ratio:.2%}" for name, ratio in report["lane_completion"].items()
    ))
    for anomaly in report["quota_anomalies"]:
        print(f"  ⚠ 配额异常：{anomaly}")
    labels = {
        "catalog": "目录",
        "safety": "急症类别",
        "population": "人群",
        "expression": "表达族",
        "complexity": "复杂病例",
    }
    for name, ratio in report["dimension_coverage"].items():
        print(f"  {labels[name]} {report['dimensions'][name]} 种，目标完成 {ratio:.2%}")
    print(f"  工程矩阵覆盖 {report['weighted_coverage']:.2%}，"
          f"状态 {report['engineering_coverage_status']}")
    print(f"  临床发布证据状态 {report['clinical_coverage_status']}，"
          f"临床金标 {report['clinical_gold_rows']}，真实流量 {report['real_traffic_rows']}")
    if not report["publishable"]:
        print("  ⚠ 工程 taxonomy 完成度不是正式真实患者覆盖率："
              "需要 ≥500 条双人临床金标和去标识真实流量")

    print("  年龄分布：" + "，".join(
        f"{key} {value}" for key, value in report["age_distribution"].items()
    ))
    print("  性别分布：" + "，".join(
        f"{key} {value}" for key, value in report["gender_distribution"].items()
    ))
    for warning in report["imbalance_warnings"]:
        print(f"  ⚠ 失衡：{warning}")
    for name, gaps in report["taxonomy_cross_cell_gaps"].items():
        print(f"  taxonomy 交叉缺口 {name}: {len(gaps)} 个单元")


def metrics(rows: List[dict]) -> None:
    print("\n=== 4. 指标 ===")
    countable = [
        r for r in rows
        if r["countable"] == "1"
        and r.get("synthetic_mapping_only") != "1"
        and _is_coverage_eligible(r)
    ]
    hits = [r for r in countable if r["gold_hit"] == "1"]
    print(f"出卡且计入分母的行 {len(countable)}，二级科命中 {len(hits)}"
          f"（Top1 {len(hits) / max(1, len(countable)):.2%}）")
    dept_hits = [r for r in countable if r["gold_department"] == r["expect_department"]]
    print(f"一级科 Top1 {len(dept_hits) / max(1, len(countable)):.2%}")

    print("\n按集合：")
    per_suite: Dict[str, Counter] = defaultdict(Counter)
    for row in rows:
        per_suite[row["suite"]]["total"] += 1
        if row["countable"] == "1" and _is_coverage_eligible(row):
            per_suite[row["suite"]]["countable"] += 1
            if row["gold_hit"] == "1":
                per_suite[row["suite"]]["hit"] += 1
    for suite in sorted(per_suite):
        stat = per_suite[suite]
        rate = (stat["hit"] / stat["countable"]) if stat["countable"] else 0
        print(f"  {suite:<20} 行数 {stat['total']:>6}  计入 {stat['countable']:>6}  Top1 {rate:.2%}")

    def _group(name: str, pred) -> None:
        picked = [
            r for r in rows
            if r["countable"] == "1"
            and _is_coverage_eligible(r)
            and pred(r["suite"])
        ]
        hit = [r for r in picked if r["gold_hit"] == "1"]
        if picked:
            print(f"  {name}：计入 {len(picked)}，命中 {len(hit)}，Top1 {len(hit) / len(picked):.2%}")

    SLANG_PREFIX = ("S19", "S20", "S21", "S22", "S23", "S24", "S25")
    CLOSED = (
        "S26", "S27", "S28", "S29", "S30", "S31", "S32", "S33",
        "S34", "S35", "S36", "S37", "S38", "S39", "S40", "S41",
    )
    print("\n口径分层：")
    _group("书面语（其余集合）", lambda s: not s.startswith(SLANG_PREFIX) and not s.startswith(CLOSED))
    _group("口语扰动（S19–S25）", lambda s: s.startswith(SLANG_PREFIX))
    _group("闭环/临床候选（S26–S41）", lambda s: s.startswith(CLOSED))

    print("\n走模型以后（计入分母，按 N6 最终来源）：")
    by_source: Dict[str, Counter] = defaultdict(Counter)
    for row in countable:
        source = row.get("expect_route_source") or "(空)"
        by_source[source]["n"] += 1
        if row.get("gold_hit") == "1":
            by_source[source]["hit"] += 1
    for source in sorted(by_source):
        stat = by_source[source]
        rate = stat["hit"] / stat["n"] if stat["n"] else 0
        print(f"  {source:<10} 计入 {stat['n']:>6}  命中 {stat['hit']:>6}  Top1 {rate:.2%}")
    model_rows = [r for r in countable if r.get("expect_route_source") == "MODEL"]
    if model_rows:
        by_model: Dict[str, Counter] = defaultdict(Counter)
        by_suite: Dict[str, Counter] = defaultdict(Counter)
        for row in model_rows:
            name = row.get("expect_model_name") or "(未记模型名)"
            by_model[name]["n"] += 1
            by_suite[row.get("suite") or "(空)"]["n"] += 1
            if row.get("gold_hit") == "1":
                by_model[name]["hit"] += 1
                by_suite[row.get("suite") or "(空)"]["hit"] += 1
        print("  按模型实现：")
        for name in sorted(by_model):
            stat = by_model[name]
            rate = stat["hit"] / stat["n"] if stat["n"] else 0
            print(f"    {name:<22} 计入 {stat['n']:>6}  命中 {stat['hit']:>6}  Top1 {rate:.2%}")
        print("  走模型按集合：")
        for suite in sorted(by_suite):
            stat = by_suite[suite]
            rate = stat["hit"] / stat["n"] if stat["n"] else 0
            print(f"    {suite:<22} 计入 {stat['n']:>6}  命中 {stat['hit']:>6}  Top1 {rate:.2%}")
        model_miss = [r for r in model_rows if r.get("gold_hit") != "1"]
        print(f"  走模型未命中 {len(model_miss)} 行，抽样：")
        seen = set()
        for row in model_miss:
            key = (row.get("suite"), row.get("input"))
            if key in seen:
                continue
            seen.add(key)
            print(
                f"    [{row.get('suite')}] 「{row.get('input')}」 "
                f"现网={row.get('expect_specialty')} 金标={row.get('gold_specialty')} "
                f"原因={row.get('expect_route_reason')} 模型={row.get('expect_model_name')}"
            )
            if len(seen) >= 12:
                break

    replay_n = spec_n = multi_n = single_n = 0
    for row in rows:
        if replayable(row):
            replay_n += 1
            if (row.get("prior_input") or "") or int(row.get("turn") or 1) > 1:
                multi_n += 1
            else:
                single_n += 1
        else:
            spec_n += 1
    print("\n用法分层（保证多轮走 prior，确认/派单不 flatten）：")
    print(f"  单轮 MESSAGE 重放 {single_n}")
    print(f"  多轮 MESSAGE 重放 {multi_n}（turn/prior_input）")
    print(f"  状态机 SPEC {spec_n}（CONFIRM/PATCH/DISPATCH/ORDER/出卡后再说话）")
    print(f"  合成隔离 {sum(1 for r in rows if r.get('synthetic_mapping_only') == '1')}（不进 Top1）")

    safety_gold = [
        r for r in rows
        if _is_coverage_eligible(r)
        and r.get("gold_safety_status") in ("URGENT", "NEED_HUMAN", "UNSUITABLE")
    ]
    safety_hit = [r for r in safety_gold if r.get("expect_safety_status") == r.get("gold_safety_status")]
    if safety_gold:
        print(f"  安全金标 {len(safety_gold)}，与现网一致 {len(safety_hit)}"
              f"（{len(safety_hit) / len(safety_gold):.2%}；目录外红旗允许不一致）")

    topk = [
        r for r in rows
        if _is_coverage_eligible(r)
        and r.get("expected_candidates_ordered")
        and r.get("gold_specialty")
    ]
    topk_hit = [r for r in topk if r["gold_specialty"] in (r.get("expected_candidates_ordered") or "").split("|")
                or r["gold_specialty"] == r.get("expect_specialty")]
    if topk:
        print(f"  Top-k 金标落在候选/Top1 {len(topk_hit)}/{len(topk)}（{len(topk_hit) / len(topk):.2%}）")

    failures = Counter()
    samples = {}
    for row in rows:
        if (
            row.get("countable") != "1"
            or row.get("gold_hit") == "1"
            or not _is_coverage_eligible(row)
        ):
            continue
        enriched = enrich_coverage_fields(row)
        key = (enriched["intent_id"], enriched["expression_family"])
        failures[key] += 1
        samples.setdefault(key, row.get("input") or "")
    if failures:
        print("\n失败案例簇（同一意图重复行合并）：")
        for key, count in failures.most_common(10):
            print(f"  {key[0]}/{key[1]} {count} 行：{samples[key]}")

    print("\n状态分布：")
    for name, count in Counter(r["expect_status_name"] for r in rows).most_common():
        print(f"  {name or '（空）':<10} {count}")

    print("\nN6 原因码 TOP15：")
    for name, count in Counter(r["expect_route_reason"] for r in rows).most_common(15):
        print(f"  {name or '（空）':<22} {count}")

    print("\n节点分布：")
    for name, count in Counter(r["node"] for r in rows).most_common():
        print(f"  {name:<6} {count}")

    uncertain = [r for r in rows if _is_coverage_eligible(r)
                 and r["countable"] == "0" and r["gold_specialty"]
                 and r["expect_specialty"] != r["gold_specialty"]]
    if uncertain:
        print(f"\n不计入分母但金标不一致（词条/扰动导致）{len(uncertain)} 行，抽样：")
        for row in uncertain[:8]:
            print(f"  {row['case_id']} 「{row['input']}」 期望 {row['expect_specialty']} 金标 {row['gold_specialty']}")


def release_assessment(rows: List[dict]) -> None:
    print("\n=== 6. 发布门禁 ===")
    coverage_rows = [row for row in rows if _is_coverage_eligible(row)]
    clinical_review_rows = [
        row for row in coverage_rows
        if row.get("annotator") == "clinician"
        or row.get("review_status") in {"REVIEWED", "ADJUDICATED"}
    ]
    gold = list(eligible_gold_rows(clinical_review_rows))
    review_complete = bool(gold) and all(
        row.get("annotator") == "clinician"
        and row.get("review_status") in {"REVIEWED", "ADJUDICATED"}
        and not validate_clinical_gold(row)
        for row in clinical_review_rows
    )
    agreement = agreement_report(gold)
    urgent = [row for row in gold if row.get("gold_safety_status") == "URGENT"]
    danger = [
        row for row in gold
        if row.get("gold_action") in {"EMERGENCY", "HANDOFF", "UNSUITABLE"}
    ]
    changed = [
        row for row in danger
        if int(row.get("context_turns") or row.get("turn") or 1) > 1
    ]
    result = evaluate_release_gates({
        "urgent_reference": len(urgent),
        "urgent_missed": sum(row.get("expect_safety_status") != "URGENT" for row in urgent),
        "danger_reference": len(danger),
        "danger_underestimated": sum(
            row.get("expected_action") not in {"EMERGENCY", "HANDOFF", "UNSUITABLE"}
            for row in danger
        ),
        "wrong_booking_reference": len(danger),
        "wrong_booking_blocked": sum(
            row.get("expect_status_name") not in {"卡片待确认", "已确认"}
            for row in danger
        ),
        "change_reference": len(changed),
        "change_responded": sum(
            row.get("expected_action") in {"EMERGENCY", "HANDOFF", "UNSUITABLE"}
            for row in changed
        ),
        "clinical_gold_rows": len(gold),
        "clinical_kappa": agreement["cohen_kappa"],
        "clinical_review_complete": review_complete,
    })
    for name, evidence in result["evidence"].items():
        print(f"  {name}: {'通过' if evidence['passed'] else '未通过'} {evidence}")
    print("  发布结论：" + ("通过" if result["passed"] else "阻断：" + "、".join(result["failed_gates"])))


def assertions(rows: List[dict]) -> None:
    """按 spec_assert 算各集合的通过率，并列出不成立的行。"""
    print("\n=== 5. 断言通过率（边界/负向/安全/槽位）===")
    stat = defaultdict(lambda: [0, 0])
    bad = defaultdict(list)
    for row in rows:
        token = row.get("spec_assert", "")
        if not token:
            continue
        suite = row["suite"]
        stat[suite][0] += 1
        if not evaluate(token, row):
            stat[suite][1] += 1
            bad[suite].append(row)
    for suite in sorted(stat):
        total, failed = stat[suite]
        print(f"  {suite:<20} 断言 {total:>6}  不成立 {failed:>5}  通过率 {(total - failed) / total:.2%}")
    print("\n  不成立抽样（每行 = 规格期望 vs 现网产物）：")
    for suite in sorted(bad):
        if not bad[suite]:
            continue
        seen = set()
        shown = 0
        for row in bad[suite]:
            key = (row["case_ref"], row["expect_specialty"], row["expect_route_reason_rule"],
                   row["expect_status_code"])
            if key in seen:
                continue
            seen.add(key)
            print(f"  [{suite}] {row['case_ref']} 「{row['input']}」\n      断言={row['spec_assert']} 现网=二级[{row['expect_specialty']}] 原因[{row['expect_route_reason_rule']}/{row['expect_route_reason']}] 状态[{row['expect_status_name']}] 安全[{row['expect_safety_status']}]")
            shown += 1
            if shown >= 6:
                break


def main(path: pathlib.Path = CSV_PATH) -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:  # pragma: no cover
        pass
    rows = load_rows(path)
    replica = Replica()
    print(f"读入 {len(rows)} 行：{path}")
    taxonomy = load_taxonomy()
    urgent_version = json.loads(URGENT_JSON.read_text(encoding="utf-8")).get("catalogVersion", "")
    print(f"版本：科室目录 {replica.snapshot.version}｜急症目录 {urgent_version}｜"
          f"覆盖本体 {taxonomy['version']}｜表达词典 {LEXICON_VERSION}")
    ok = run_self_check(replica)
    coverage(rows, replica)
    diffs = replay(rows)
    metrics(rows)
    assertions(rows)
    release_assessment(rows)
    print("\n结论：" + ("自检通过" if ok else "自检未通过") + "，重放"
          + ("一致" if not diffs else f"有 {sum(diffs.values())} 处不一致"))
    return 0 if (ok and not diffs) else 1


if __name__ == "__main__":
    target = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else CSV_PATH
    sys.exit(main(target))
