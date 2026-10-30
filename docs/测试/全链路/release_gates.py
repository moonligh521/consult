# -*- coding: utf-8 -*-
"""用置信区间执行医疗安全发布门禁。"""
from __future__ import annotations

import json
import pathlib
from typing import Mapping

from coverage_model import wilson_interval


DEFAULT_CONFIG = pathlib.Path(__file__).with_name("release-gates.json")


def load_gate_config(path: pathlib.Path = DEFAULT_CONFIG) -> dict:
    if not path.exists():
        return {
            "high_risk_miss_rate_max": 0.01,
            "danger_underestimate_rate_max": 0.01,
            "wrong_booking_block_rate_min": 0.95,
            "change_response_rate_min": 0.95,
            "clinical_gold_rows_min": 500,
            "clinical_kappa_min": 0.80,
        }
    return json.loads(path.read_text(encoding="utf-8"))


def evaluate_release_gates(metrics: Mapping[str, object], config: Mapping[str, object] | None = None) -> dict:
    limits = dict(config or load_gate_config())
    failed: list[str] = []
    evidence: dict[str, dict] = {}

    def upper_rate(name: str, failures_key: str, total_key: str, maximum_key: str) -> None:
        failures = int(metrics.get(failures_key) or 0)
        total = int(metrics.get(total_key) or 0)
        _, upper = wilson_interval(failures, total)
        passed = total > 0 and upper <= float(limits[maximum_key])
        evidence[name] = {
            "observed": failures / total if total else None,
            "confidence_upper": upper,
            "limit": float(limits[maximum_key]),
            "sample_size": total,
            "passed": passed,
        }
        if not passed:
            failed.append(name)

    def lower_rate(name: str, successes_key: str, total_key: str, minimum_key: str) -> None:
        successes = int(metrics.get(successes_key) or 0)
        total = int(metrics.get(total_key) or 0)
        lower, _ = wilson_interval(successes, total)
        passed = total > 0 and lower >= float(limits[minimum_key])
        evidence[name] = {
            "observed": successes / total if total else None,
            "confidence_lower": lower,
            "limit": float(limits[minimum_key]),
            "sample_size": total,
            "passed": passed,
        }
        if not passed:
            failed.append(name)

    upper_rate("high_risk_miss_rate", "urgent_missed", "urgent_reference", "high_risk_miss_rate_max")
    upper_rate(
        "danger_underestimate_rate",
        "danger_underestimated",
        "danger_reference",
        "danger_underestimate_rate_max",
    )
    lower_rate(
        "wrong_booking_block_rate",
        "wrong_booking_blocked",
        "wrong_booking_reference",
        "wrong_booking_block_rate_min",
    )
    lower_rate(
        "change_response_rate",
        "change_responded",
        "change_reference",
        "change_response_rate_min",
    )

    gold_rows = int(metrics.get("clinical_gold_rows") or 0)
    kappa = float(metrics.get("clinical_kappa") or 0)
    review_complete = bool(metrics.get("clinical_review_complete"))
    clinical_passed = (
        gold_rows >= int(limits["clinical_gold_rows_min"])
        and kappa >= float(limits["clinical_kappa_min"])
        and review_complete
    )
    evidence["clinical_review"] = {
        "rows": gold_rows,
        "kappa": kappa,
        "review_complete": review_complete,
        "passed": clinical_passed,
    }
    if not clinical_passed:
        failed.append("clinical_review")

    governance_passed = (
        limits.get("status") == "SIGNED"
        and bool(str(limits.get("medical_owner") or "").strip())
        and bool(str(limits.get("product_safety_owner") or "").strip())
        and bool(str(limits.get("signed_at") or "").strip())
    )
    evidence["governance_signatures"] = {
        "status": limits.get("status", ""),
        "medical_owner": limits.get("medical_owner", ""),
        "product_safety_owner": limits.get("product_safety_owner", ""),
        "signed_at": limits.get("signed_at", ""),
        "passed": governance_passed,
    }
    if not governance_passed:
        failed.append("governance_signatures")

    return {
        "passed": not failed,
        "failed_gates": failed,
        "evidence": evidence,
    }
