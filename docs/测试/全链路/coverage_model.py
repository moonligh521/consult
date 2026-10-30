# -*- coding: utf-8 -*-
"""真实医疗场景覆盖口径、行级归类与去重统计。"""
from __future__ import annotations

import hashlib
import json
import math
import pathlib
import re
import unicodedata
from collections import Counter
from itertools import product
from typing import Iterable, Mapping

from quota_model import load_quota


COVERAGE_WEIGHTS = {
    "catalog": 0.20,
    "safety": 0.25,
    "population": 0.20,
    "expression": 0.20,
    "complexity": 0.15,
}

CLINICAL_CATEGORIES = {
    "cardiovascular": "胸痛与心血管",
    "respiratory": "呼吸危象",
    "neurological": "卒中、抽搐与意识障碍",
    "hemorrhage": "严重出血",
    "allergy_airway": "严重过敏与窒息",
    "pediatric_emergency": "儿童急症",
    "obstetric_emergency": "孕产急症",
    "self_harm_poisoning": "自伤、自杀与过量服药",
    "trauma": "严重外伤",
    "poisoning": "中毒",
    "acute_abdomen": "急腹症",
    "high_risk_vague": "其他高危含糊表达",
}

AGE_BANDS = ("infant", "child", "adolescent", "18-39", "40-59", "60-79", "80+")

EXPRESSION_FAMILIES = (
    "standard",
    "colloquial",
    "dialect",
    "typo",
    "pinyin",
    "english",
    "asr",
    "negation",
    "family_report",
    "multiturn",
    "mixed",
)

DEFAULT_TAXONOMY = pathlib.Path(__file__).with_name("coverage-taxonomy.json")


def load_taxonomy(path: pathlib.Path = DEFAULT_TAXONOMY) -> dict:
    taxonomy = json.loads(path.read_text(encoding="utf-8"))
    weights = taxonomy.get("coverage_weights") or {}
    if abs(sum(float(value) for value in weights.values()) - 1.0) > 1e-9:
        raise ValueError("coverage_weights 之和必须为 1")
    return taxonomy

_DIRECT_CATEGORY = {
    "URG-BASE-001": "cardiovascular",
    "URG-DRAFT-001": "cardiovascular",
    "URG-BASE-002": "respiratory",
    "URG-DRAFT-002": "respiratory",
    "URG-BASE-003": "neurological",
    "URG-DRAFT-003": "neurological",
    "URG-DRAFT-004": "hemorrhage",
    "URG-DRAFT-005": "allergy_airway",
    "URG-DRAFT-006": "pediatric_emergency",
    "URG-DRAFT-007": "self_harm_poisoning",
    "HUMAN-BASE-002": "high_risk_vague",
}

_CATEGORY_HINTS = (
    ("obstetric_emergency", ("怀孕", "孕妇", "产后", "阴道大出血")),
    ("self_harm_poisoning", ("想死", "自杀", "轻生", "割腕", "吃了一瓶药", "过量服药")),
    ("trauma", ("车祸", "刀砍", "外伤", "摔伤", "头破")),
    ("hemorrhage", ("吐血", "咯血", "咳血", "便血", "黑便", "大出血", "流血不止")),
    ("allergy_airway", ("喉头肿", "过敏", "窒息", "咽喉肿")),
    ("pediatric_emergency", ("高热惊厥", "孩子抽筋", "宝宝抽搐")),
    ("neurological", ("意识障碍", "叫不醒", "晕倒", "口角歪", "说话含糊", "抽搐")),
    ("respiratory", ("呼吸困难", "喘不上气", "憋气", "口唇发紫")),
    ("cardiovascular", ("胸痛", "心口疼", "胸口闷")),
    ("acute_abdomen", ("剧烈腹痛", "板状腹", "急腹症")),
    ("poisoning", ("中毒", "误服", "农药")),
)

_PHONE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
_AGE = re.compile(r"(?:男|女)?[，,\s]*\d{1,3}\s*岁[，,\s]*")
_GENDER = re.compile(r"^(?:患者|本人|孩子)?[，,\s]*(?:男|女)[，,\s]*")
_DURATION = re.compile(
    r"(?:了|已经|持续)?(?:半个月|半年|半天|"
    r"[一二两三四五六七八九十百\d]+\s*(?:小时|天|周|个月|月|年))(?:了)?$"
)
_PUNCT = re.compile(r"[\s，。；、,.!?！？：:（）()\"'`]+")
_TRAILING_PUNCT = re.compile(r"[\s，。；、,.!?！？：:（）()\"'`]+$")
_CARRIER_PREFIXES = (
    "医生您好", "情况是这样的", "想咨询一下", "先说明一下", "麻烦问下",
    "帮忙看看", "我想挂号", "请问", "你好",
)


def _digest(value: str, size: int = 16) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()[:size]


def normalize_text(text: str) -> str:
    """删除不改变医学意图的槽位包装，供近重复聚类使用。"""
    value = unicodedata.normalize("NFKC", text or "").strip()
    value = _PHONE.sub("[PHONE]", value)
    value = _AGE.sub("", value)
    value = _GENDER.sub("", value)
    # 先去掉句末标点，避免它挡住以行尾为锚点的病程匹配。
    value = _TRAILING_PUNCT.sub("", value)
    value = _DURATION.sub("", value)
    value = _PUNCT.sub("", value).lower()
    changed = True
    while changed:
        changed = False
        for prefix in _CARRIER_PREFIXES:
            if value.startswith(prefix):
                value = value[len(prefix):]
                changed = True
                break
    return value


def age_band(value: object) -> str:
    try:
        age = int(str(value))
    except (TypeError, ValueError):
        return "unknown"
    if age < 1:
        return "invalid"
    if age <= 2:
        return "infant"
    if age <= 14:
        return "child"
    if age <= 17:
        return "adolescent"
    if age <= 39:
        return "18-39"
    if age <= 59:
        return "40-59"
    if age <= 79:
        return "60-79"
    if age <= 120:
        return "80+"
    return "invalid"


def infer_clinical_category(row: Mapping[str, object]) -> str:
    explicit = str(row.get("clinical_category") or "")
    if explicit in CLINICAL_CATEGORIES:
        return explicit
    rule = str(row.get("safety_rule_id") or "")
    if rule in _DIRECT_CATEGORY:
        return _DIRECT_CATEGORY[rule]
    text = str(row.get("input") or "")
    for category, hints in _CATEGORY_HINTS:
        if any(hint in text for hint in hints):
            return category
    return "routine"


def infer_expression_family(row: Mapping[str, object]) -> str:
    explicit = str(row.get("expression_family") or "")
    if explicit in EXPRESSION_FAMILIES:
        return explicit
    suite = str(row.get("suite") or "")
    tone = str(row.get("variant_tone") or "")
    noise = str(row.get("variant_noise") or "")
    text = str(row.get("input") or "")
    if int(str(row.get("turn") or "1")) > 1 or row.get("prior_input"):
        return "multiturn"
    if suite.startswith("S19"):
        return "pinyin"
    if suite.startswith("S20"):
        return "english"
    if suite.startswith("S21") or "TYP" in noise:
        return "typo"
    if "DIALECT" in tone or suite.startswith("S35"):
        return "dialect"
    if "ASR" in tone:
        return "asr"
    if suite.startswith("S30") or "否定" in tone or any(word in text for word in ("没有", "不是", "否认")):
        return "negation"
    if "代述" in tone or any(word in text for word in ("我爸", "我妈", "孩子", "家里人")):
        return "family_report"
    if suite.startswith(("S22", "S23", "S24", "S25")):
        return "mixed"
    if any(word in tone for word in ("口语", "提问", "补述")):
        return "colloquial"
    return "standard"


def _risk_level(row: Mapping[str, object], category: str) -> str:
    safety = str(row.get("gold_safety_status") or row.get("expect_safety_status") or "")
    if safety == "URGENT":
        return "RED"
    if safety in ("NEED_HUMAN", "UNSUITABLE", "SAFE_FALLBACK"):
        return "ORANGE"
    if category != "routine":
        return "YELLOW"
    return "GREEN"


def enrich_coverage_fields(row: Mapping[str, object]) -> dict:
    """返回带覆盖维度的副本，不修改调用方对象。"""
    enriched = dict(row)
    text = str(row.get("input") or "")
    normalized = normalize_text(text)
    category = infer_clinical_category(row)
    expression = infer_expression_family(row)
    age = row.get("slot_age") or row.get("session_age") or row.get("age")
    population = age_band(age)
    gender = str(
        row.get("slot_gender")
        or row.get("session_gender")
        or row.get("gender")
        or "unknown"
    )
    specialty = str(row.get("gold_specialty") or row.get("expect_specialty") or "")
    rule = str(row.get("safety_rule_id") or "")
    intent_key = "|".join((category, normalized, specialty, rule))
    intent_id = str(row.get("intent_id") or f"INT-{_digest(intent_key)}")
    scenario_key = "|".join((intent_id, population, gender, expression))
    safety = str(row.get("gold_safety_status") or row.get("expect_safety_status") or "")

    enriched.update({
        "clinical_category": category,
        "risk_level": str(row.get("risk_level") or _risk_level(row, category)),
        "reference_action": str(
            row.get("reference_action")
            or ("EMERGENCY" if safety == "URGENT" else row.get("gold_action") or row.get("expected_action") or "")
        ),
        "time_window": str(row.get("time_window") or ("IMMEDIATE" if safety == "URGENT" else "")),
        "atypical": str(row.get("atypical") or ("1" if expression != "standard" else "0")),
        "population_group": str(
            row.get("population_group") or row.get("age_band") or population
        ),
        "expression_family": expression,
        "intent_id": intent_id,
        "scenario_id": str(row.get("scenario_id") or f"SCN-{_digest(scenario_key)}"),
        "normalized_hash": str(row.get("normalized_hash") or _digest(normalized)),
        "source_kind": str(row.get("source_kind") or (
            "clinical" if row.get("annotator") == "clinician" else "synthetic"
        )),
        "evidence_source": str(row.get("evidence_source") or (
            "clinical-review" if row.get("annotator") == "clinician" else "rule-replica"
        )),
    })
    return enriched


def wilson_interval(successes: int, total: int, z: float = 1.959963984540054) -> tuple[float, float]:
    if total <= 0:
        return 0.0, 1.0
    rate = successes / total
    denominator = 1 + z * z / total
    center = (rate + z * z / (2 * total)) / denominator
    margin = z * math.sqrt((rate * (1 - rate) + z * z / (4 * total)) / total) / denominator
    return max(0.0, center - margin), min(1.0, center + margin)


def _is_coverage_eligible(row: Mapping[str, object]) -> bool:
    """兼容旧数据：仅显式排除或 capacity 行不参与有效覆盖。"""
    value = row.get("coverage_eligible")
    explicitly_excluded = value is False or str(value).strip().lower() in {"0", "false", "no"}
    return not explicitly_excluded and str(row.get("primary_lane") or "") != "capacity"


def _gender(value: object) -> str:
    normalized = str(value or "").strip()
    return {"1": "男", "2": "女", "male": "男", "female": "女"}.get(
        normalized.lower(), normalized or "unknown"
    )


def _cross_cell_gaps(rows: list[dict], taxonomy: Mapping[str, object]) -> dict:
    dimension_values = {
        "clinical_category": tuple(taxonomy["clinical_categories"]),
        "expression_family": tuple(taxonomy["expression_families"]),
        "population_group": tuple(taxonomy["population_groups"]),
        "complexity_type": tuple(taxonomy["complexity_types"]),
    }
    gaps = {}
    for name, target in taxonomy.get("cross_targets", {}).items():
        dimensions = tuple(target["dimensions"])
        required = int(target["minimum_independent_intents_per_cell"])
        cells: dict[tuple[str, ...], set[str]] = {}
        for row in rows:
            cell = tuple(str(row.get(dimension) or "") for dimension in dimensions)
            cells.setdefault(cell, set()).add(str(row["intent_id"]))
        missing = []
        for cell in product(*(dimension_values[dimension] for dimension in dimensions)):
            count = len(cells.get(cell, set()))
            if count < required:
                missing.append({
                    "cell": dict(zip(dimensions, cell)),
                    "independent_intents": count,
                    "required": required,
                    "missing": required - count,
                })
        gaps[name] = missing
    return gaps


def _engineering_coverage_status(
    dimension_coverage: Mapping[str, float],
    cross_gaps: Mapping[str, object],
    quota_anomalies: list[str],
    imbalance_warnings: list[str],
) -> str:
    complete = (
        all(value == 1.0 for value in dimension_coverage.values())
        and not any(cross_gaps.values())
        and not quota_anomalies
        and not imbalance_warnings
    )
    return "COMPLETE" if complete else "INCOMPLETE"


def coverage_report(rows: Iterable[Mapping[str, object]]) -> dict:
    taxonomy = load_taxonomy()
    quota = load_quota()
    source_rows = list(rows)
    # S2 是由启用目录生成的 137 科规格金标，可作为本报告内的目录白名单；
    # 其他行只能命中该集合，不能凭任意字符串扩充目录覆盖。
    allowed_specialties = {
        str(row.get("gold_specialty") or "").strip()
        for row in source_rows
        if str(row.get("suite") or "").startswith("S2-规格金标137")
        and str(row.get("gold_specialty") or "").strip()
    }
    allowed_complexities = set(taxonomy["complexity_types"])
    raw_rows = 0
    eligible_rows: list[dict] = []
    lane_counts: Counter = Counter()
    intents: set[str] = set()
    scenarios: set[str] = set()
    clusters: Counter = Counter()
    normalized_hashes: set[str] = set()
    trajectories: set[tuple[str, str]] = set()
    specialties: set[str] = set()
    safety_categories: set[str] = set()
    populations: set[str] = set()
    expressions: set[str] = set()
    complexities: set[str] = set()
    age_distribution: Counter = Counter()
    gender_distribution: Counter = Counter()
    specialty_distribution: Counter = Counter()
    safety_distribution: Counter = Counter()
    expression_distribution: Counter = Counter()
    specialty_balance_distribution: Counter = Counter()
    safety_balance_distribution: Counter = Counter()
    population_balance_distribution: Counter = Counter()
    expression_balance_distribution: Counter = Counter()
    gender_balance_distribution: Counter = Counter()
    unknown_specialties: set[str] = set()
    unknown_complexities: set[str] = set()
    clinical_rows = 0
    real_rows = 0
    for source in source_rows:
        row = enrich_coverage_fields(source)
        raw_rows += 1
        lane = str(row.get("primary_lane") or "unassigned")
        lane_counts[lane] += 1
        if not _is_coverage_eligible(row):
            continue
        eligible_rows.append(row)
        intents.add(row["intent_id"])
        scenarios.add(row["scenario_id"])
        if lane == "state_machine":
            cluster_key = (
                lane,
                row["intent_id"],
                str(row.get("action") or ""),
                str(row.get("scenario") or ""),
            )
        elif lane == "complex_multiturn":
            cluster_key = (
                lane,
                row["intent_id"],
                row["expression_family"],
                str(row.get("complexity_type") or ""),
                str(row.get("prior_input") or ""),
                str(row.get("input") or ""),
            )
        elif lane == "population":
            cluster_key = (
                lane,
                row["scenario_id"],
                row["expression_family"],
            )
        else:
            cluster_key = (lane, row["intent_id"], row["expression_family"])
        clusters[cluster_key] += 1
        normalized_hashes.add(row["normalized_hash"])
        if int(str(row.get("turn") or "1")) > 1 or row.get("prior_input"):
            trajectories.add((
                str(row.get("prior_input") or ""),
                str(row.get("input") or ""),
            ))
        specialty = str(row.get("gold_specialty") or "").strip()
        if specialty:
            if specialty in allowed_specialties:
                specialties.add(specialty)
                specialty_distribution[specialty] += 1
                if lane in {"catalog_routine", "unassigned"}:
                    specialty_balance_distribution[specialty] += 1
            else:
                unknown_specialties.add(specialty)
        if row["clinical_category"] != "routine":
            safety_categories.add(row["clinical_category"])
            safety_distribution[row["clinical_category"]] += 1
            if lane in {"safety", "unassigned"}:
                safety_balance_distribution[row["clinical_category"]] += 1
        if row["population_group"] in AGE_BANDS:
            populations.add(row["population_group"])
        age_distribution[row["population_group"]] += 1
        if lane in {"population", "unassigned"}:
            population_balance_distribution[row["population_group"]] += 1
        normalized_gender = _gender(
            row.get("slot_gender") or row.get("session_gender") or row.get("gender")
        )
        gender_distribution[normalized_gender] += 1
        if lane in {"population", "unassigned"}:
            gender_balance_distribution[normalized_gender] += 1
        expressions.add(row["expression_family"])
        expression_distribution[row["expression_family"]] += 1
        if lane in {"expression", "unassigned"}:
            expression_balance_distribution[row["expression_family"]] += 1
        complexity = str(row.get("complexity_type") or "").strip()
        if complexity:
            if complexity in allowed_complexities:
                complexities.add(complexity)
            else:
                unknown_complexities.add(complexity)
        clinical_rows += bool(
            row.get("annotator") == "clinician"
            and row.get("review_status") in {"REVIEWED", "ADJUDICATED"}
        )
        real_rows += row.get("source_kind") == "real-traffic-deidentified"
    dimensions = {
        "catalog": len(specialties),
        "safety": len(safety_categories),
        "population": len(populations),
        "expression": len(expressions),
        "complexity": len(complexities),
    }
    targets = taxonomy["targets"]
    dimension_coverage = {
        "catalog": min(1.0, dimensions["catalog"] / targets["catalog_specialties"]),
        "safety": min(1.0, dimensions["safety"] / targets["clinical_categories"]),
        "population": min(1.0, dimensions["population"] / targets["population_groups"]),
        "expression": min(1.0, dimensions["expression"] / targets["expression_families"]),
        "complexity": min(1.0, dimensions["complexity"] / targets["complexity_types"]),
    }
    weighted = sum(
        dimension_coverage[name] * float(taxonomy["coverage_weights"][name])
        for name in dimension_coverage
    )
    cross_gaps = _cross_cell_gaps(eligible_rows, taxonomy)
    publishable = clinical_rows >= 500 and real_rows > 0
    if publishable:
        clinical_status = "PUBLISHABLE"
    elif clinical_rows >= 500:
        clinical_status = "CLINICAL_ONLY"
    elif real_rows > 0:
        clinical_status = "REAL_TRAFFIC_UNREVIEWED"
    else:
        clinical_status = "EXPERT_PRIOR_ONLY"

    quota_by_lane = {lane["lane"]: int(lane["quota"]) for lane in quota["lanes"]}
    lane_completion = {
        lane: lane_counts.get(lane, 0) / target
        for lane, target in quota_by_lane.items()
    }
    quota_anomalies = []
    if raw_rows != int(quota["total_quota"]):
        quota_anomalies.append(
            f"总行数 {raw_rows}，要求精确 {quota['total_quota']}"
        )
    for lane, target in quota_by_lane.items():
        actual = lane_counts.get(lane, 0)
        if actual != target:
            quota_anomalies.append(f"{lane} {actual}，配额 {target}")
    unexpected_lanes = sorted(set(lane_counts) - set(quota_by_lane))
    if unexpected_lanes:
        quota_anomalies.append("未配置 lane：" + "、".join(unexpected_lanes))

    max_cluster_size = max(clusters.values(), default=0)
    max_cluster_share = max_cluster_size / len(eligible_rows) if eligible_rows else 0.0
    imbalance_warnings = []
    rules = taxonomy["forbidden_imbalance_rules"]

    def warn_distribution(
        label: str, distribution: Counter, threshold: float
    ) -> None:
        total = sum(distribution.values())
        if not total:
            return
        key, count = distribution.most_common(1)[0]
        share = count / total
        if share > threshold:
            imbalance_warnings.append(
                f"{label} {key} 占比 {share:.2%}，超过 {threshold:.2%}"
            )

    warn_distribution(
        "二级科",
        specialty_balance_distribution,
        float(rules["single_specialty_share_max"]),
    )
    warn_distribution(
        "急症类别",
        safety_balance_distribution,
        float(rules["single_safety_category_share_max"]),
    )
    warn_distribution(
        "年龄层",
        population_balance_distribution,
        float(rules["single_population_group_share_max"]),
    )
    warn_distribution(
        "表达族",
        expression_balance_distribution,
        float(rules["single_expression_family_share_max"]),
    )
    known_gender = Counter({
        key: value for key, value in gender_balance_distribution.items()
        if key in {"男", "女"}
    })
    warn_distribution("性别", known_gender, 0.80)
    if max_cluster_share > float(rules["single_duplicate_cluster_share_max"]):
        imbalance_warnings.append(
            f"最大重复簇占比 {max_cluster_share:.2%}，超过 "
            f"{float(rules['single_duplicate_cluster_share_max']):.2%}"
        )
    engineering_status = _engineering_coverage_status(
        dimension_coverage,
        cross_gaps,
        quota_anomalies,
        imbalance_warnings,
    )
    return {
        "raw_rows": raw_rows,
        "eligible_rows": len(eligible_rows),
        "unique_texts": len(normalized_hashes),
        "unique_intents": len(intents),
        "unique_scenarios": len(scenarios),
        "unique_clusters": len(clusters),
        "unique_multiturn_trajectories": len(trajectories),
        # 同一意图和表达族的重复行只贡献一个有效样本。
        "effective_samples": len(clusters),
        "max_duplicate_cluster_size": max_cluster_size,
        "max_duplicate_cluster_share": max_cluster_share,
        "lane_counts": dict(lane_counts),
        "lane_completion": lane_completion,
        "quota_anomalies": quota_anomalies,
        "age_distribution": dict(age_distribution),
        "gender_distribution": dict(gender_distribution),
        "imbalance_warnings": imbalance_warnings,
        "taxonomy_cross_cell_gaps": cross_gaps,
        "unknown_specialties": sorted(unknown_specialties),
        "unknown_complexity_types": sorted(unknown_complexities),
        "dimensions": dimensions,
        "dimension_coverage": dimension_coverage,
        "weighted_coverage": weighted,
        "engineering_coverage_status": engineering_status,
        "clinical_gold_rows": clinical_rows,
        "real_traffic_rows": real_rows,
        "clinical_coverage_status": clinical_status,
        # 兼容旧调用方；该状态始终代表临床证据，不代表工程矩阵完成度。
        "coverage_status": clinical_status,
        "publishable": publishable,
    }
