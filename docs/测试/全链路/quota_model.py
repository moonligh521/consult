# -*- coding: utf-8 -*-
"""二十万工程测试数据的互斥配额契约。"""
from __future__ import annotations

import json
import pathlib
from typing import Mapping


TOTAL_QUOTA = 200000
LANES = {
    "catalog_routine",
    "safety",
    "population",
    "expression",
    "complex_multiturn",
    "state_machine",
    "capacity",
}
DEFAULT_QUOTA = pathlib.Path(__file__).with_name("coverage-quota.json")


def _integer(value: object, field: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError(f"{field} 必须为整数")
    return value


def validate_quota(config: Mapping[str, object]) -> dict:
    """校验并返回配额配置副本；每一行只能归入一个 lane。"""
    total = _integer(config.get("total_quota"), "total_quota")
    if total != TOTAL_QUOTA:
        raise ValueError(f"total_quota 必须为 {TOTAL_QUOTA}")

    lanes = config.get("lanes")
    if not isinstance(lanes, list):
        raise ValueError("lanes 必须为列表")
    names = [lane.get("lane") for lane in lanes if isinstance(lane, Mapping)]
    if len(names) != len(lanes) or len(names) != len(set(names)):
        raise ValueError("lane 必须存在且唯一")
    if set(names) != LANES:
        raise ValueError("必须且只能配置七个主 lane")

    quota_sum = 0
    ratio_sum = 0.0
    for lane in lanes:
        name = str(lane["lane"])
        quota = _integer(lane.get("quota"), f"{name}.quota")
        minimum = _integer(
            lane.get("minimum_independent_intents"),
            f"{name}.minimum_independent_intents",
        )
        ratio = lane.get("ratio")
        eligible = lane.get("coverage_eligible")
        if quota <= 0:
            raise ValueError(f"{name}.quota 必须大于 0")
        if not isinstance(ratio, (int, float)) or isinstance(ratio, bool) or not 0 < ratio <= 1:
            raise ValueError(f"{name}.ratio 必须在 (0, 1] 内")
        if abs(float(ratio) - quota / TOTAL_QUOTA) > 1e-12:
            raise ValueError(f"{name}.ratio 必须等于 quota/total_quota")
        if minimum < 0 or minimum > quota:
            raise ValueError(f"{name}.minimum_independent_intents 必须在 [0, quota] 内")
        if not isinstance(eligible, bool):
            raise ValueError(f"{name}.coverage_eligible 必须为布尔值")
        if eligible != (name != "capacity"):
            raise ValueError("capacity 必须排除覆盖统计，其余 lane 必须计入")
        if eligible and minimum == 0:
            raise ValueError(f"{name} 必须设置正的最低独立意图目标")
        if not eligible and minimum != 0:
            raise ValueError("capacity 的最低独立意图目标必须为 0")
        quota_sum += quota
        ratio_sum += float(ratio)

    if quota_sum != TOTAL_QUOTA:
        raise ValueError(f"lane quota 之和必须为 {TOTAL_QUOTA}")
    if abs(ratio_sum - 1.0) > 1e-12:
        raise ValueError("lane ratio 之和必须为 1")
    return {
        "version": str(config.get("version") or ""),
        "total_quota": total,
        "lanes": [dict(lane) for lane in lanes],
    }


def load_quota(path: pathlib.Path = DEFAULT_QUOTA) -> dict:
    return validate_quota(json.loads(path.read_text(encoding="utf-8")))
