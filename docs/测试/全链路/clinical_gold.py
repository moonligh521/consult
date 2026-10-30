# -*- coding: utf-8 -*-
"""临床金标校验与双人标注一致性统计。"""
from __future__ import annotations

from collections import Counter
from typing import Iterable, Mapping


REQUIRED_GOLD_FIELDS = (
    "case_id",
    "clinical_reviewer",
    "reviewer_secondary",
    "review_date",
    "gold_safety_status",
    "gold_action",
    "acceptable_specialties",
    "key_evidence",
    "prohibited_outputs",
    "reviewer_primary_label",
    "reviewer_secondary_label",
)


def validate_clinical_gold(row: Mapping[str, object]) -> list[str]:
    errors = [f"缺少字段 {field}" for field in REQUIRED_GOLD_FIELDS if not str(row.get(field) or "").strip()]
    primary = str(row.get("reviewer_primary_label") or "").strip()
    secondary = str(row.get("reviewer_secondary_label") or "").strip()
    if primary and secondary and primary != secondary:
        if not str(row.get("adjudicator") or "").strip() or not str(row.get("adjudicated_label") or "").strip():
            errors.append("分歧样本必须由第三人裁决")
    if str(row.get("clinical_reviewer") or "") == str(row.get("reviewer_secondary") or ""):
        errors.append("两名审核人必须不同")
    return errors


def agreement_report(rows: Iterable[Mapping[str, object]]) -> dict:
    pairs = [
        (str(row.get("reviewer_primary_label") or ""), str(row.get("reviewer_secondary_label") or ""))
        for row in rows
    ]
    pairs = [(left, right) for left, right in pairs if left and right]
    if not pairs:
        return {"double_reviewed": 0, "observed_agreement": 0.0, "cohen_kappa": 0.0}
    observed = sum(left == right for left, right in pairs) / len(pairs)
    left_counts = Counter(left for left, _ in pairs)
    right_counts = Counter(right for _, right in pairs)
    labels = set(left_counts) | set(right_counts)
    expected = sum(
        (left_counts[label] / len(pairs)) * (right_counts[label] / len(pairs))
        for label in labels
    )
    kappa = (observed - expected) / (1 - expected) if expected < 1 else 1.0
    return {
        "double_reviewed": len(pairs),
        "observed_agreement": observed,
        "cohen_kappa": kappa,
    }


def eligible_gold_rows(rows: Iterable[Mapping[str, object]]) -> list[Mapping[str, object]]:
    eligible = []
    for row in rows:
        if row.get("annotator") != "clinician":
            continue
        if row.get("review_status") not in {"REVIEWED", "ADJUDICATED"}:
            continue
        if validate_clinical_gold(row):
            continue
        eligible.append(row)
    return eligible
