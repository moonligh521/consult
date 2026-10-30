# -*- coding: utf-8 -*-
"""专项用例的规格断言：token 定义与取值。

token（; 分隔表示或）：SPECIALTY:X / NOTSPECIALTY:X / DEPT:X /
STATUS:X(URGENT|NEED_HUMAN|CARD_READY|NEED_DESCRIPTION|NEED_SLOT|CONFIRMED) /
SAFETY:X(URGENT|NEED_HUMAN|UNSUITABLE|PASS) / SHADOW:RULEID / SLOT:X|NOSLOT /
REASON:X / FINAL:X / ERROR:CODE
"""
from __future__ import annotations

STATUS_CODE = {
    "URGENT": 3, "NEED_HUMAN": 6, "CARD_READY": 4,
    "NEED_DESCRIPTION": 1, "NEED_SLOT": 2, "CONFIRMED": 5,
}

BY_REF = {
    ("S3-边界-正向", "B1"): "SPECIALTY:呼吸内科",
    ("S3-边界-正向", "B2"): "SPECIALTY:神经内科",
    ("S3-边界-正向", "B3"): "SPECIALTY:呼吸内科",
    ("S3-边界-正向", "B4"): "SPECIALTY:消化内科",
    ("S3-边界-正向", "B5"): "SPECIALTY:小儿呼吸科",
    ("S3-边界-正向", "B6"): "SPECIALTY:小儿呼吸科",
    ("S3-边界-正向", "B7"): "DEPT:妇产科",
    ("S3-边界-正向", "B8"): "DEPT:男科",
    ("S3-边界-正向", "B9"): "SPECIALTY:中医科",
    ("S3-边界-正向", "B10"): "SPECIALTY:中医科",
    ("S3-边界-正向", "B11"): "SPECIALTY:普内科",
    ("S3-边界-正向", "B12"): "SPECIALTY:皮肤科",
    ("S3-边界-正向", "B13"): "SPECIALTY:神经内科",
    ("S3-边界-正向", "B14"): "SPECIALTY:呼吸内科",
    ("S3-边界-正向", "B15"): "SPECIALTY:普内科;SPECIALTY:全科",
    ("S3-边界-正向", "B16"): "SPECIALTY:内分泌科;SPECIALTY:全科",
    ("S3-边界-正向", "B17"): "ERROR:1104",
    ("S3-边界-正向", "B18"): "SPECIALTY:呼吸内科",
    ("S3-边界-正向", "B19"): "SPECIALTY:小儿呼吸科",
    ("S3-边界-正向", "B19b"): "SPECIALTY:呼吸内科",
    ("S3-边界-正向", "B20"): "SPECIALTY:耳鼻咽喉科",
    ("S4-边界-负向", "NB1"): "SPECIALTY:呼吸内科",
    ("S4-边界-负向", "NB2"): "SPECIALTY:呼吸内科",
    ("S4-边界-负向", "NB3"): "STATUS:NEED_DESCRIPTION",
    ("S4-边界-负向", "NB4"): "STATUS:NEED_SLOT",
    ("S4-边界-负向", "NB5"): "SLOT:AGE",
    ("S4-边界-负向", "NB5b"): "SLOT:AGE",
    ("S4-边界-负向", "NB6"): "SLOT:DURATION",
    ("S4-边界-负向", "NB7"): "SLOT:AGE",
    ("S4-边界-负向", "NB8"): "SPECIALTY:耳鼻咽喉科",
    ("S4-边界-负向", "NB9"): "SPECIALTY:全科;SPECIALTY:睡眠医学科",
    ("S4-边界-负向", "NB9b"): "SPECIALTY:中医科",
    ("S4-边界-负向", "NB10"): "SPECIALTY:结核病科",
    ("S4-边界-负向", "NB11"): "SPECIALTY:骨科",
    ("S4-边界-负向", "NB12"): "SPECIALTY:普内科;SPECIALTY:全科",
    ("S4-边界-负向", "NB13"): "SPECIALTY:全科;SPECIALTY:消化内科;SPECIALTY:口腔科",
    ("S4-边界-负向", "NB14"): "ERROR:1119",
    ("S4-边界-负向", "NB14b"): "ERROR:1119",
    ("S4-边界-负向", "NB15"): "STATUS:URGENT",
    ("S5-负向", "N1"): "SPECIALTY:全科",
    ("S5-负向", "N2"): "SPECIALTY:全科;STATUS:NEED_DESCRIPTION",
    ("S5-负向", "N3"): "SPECIALTY:全科;REASON:UNCERTAIN_MARKER",
    ("S5-负向", "N4"): "SPECIALTY:全科;REASON:UNCERTAIN_MARKER",
    ("S5-负向", "N5"): "REASON:CONFLICT",
    ("S5-负向", "N6"): "SPECIALTY:全科",
    ("S5-负向", "N7"): "SPECIALTY:全科;REASON:AMBIGUOUS_ONLY",
    ("S5-负向", "N8"): "SAFETY:PASS",
    ("S5-负向", "N9"): "SPECIALTY:呼吸内科",
    ("S5-负向", "N10"): "SPECIALTY:呼吸内科",
    ("S5-负向", "N11"): "STATUS:URGENT",
    ("S5-负向", "N12"): "STATUS:NEED_HUMAN",
    ("S5-负向", "N13"): "STATUS:NEED_HUMAN",
    ("S5-负向", "N15"): "SPECIALTY:全科",
    ("S5-负向", "N15b"): "SPECIALTY:全科",
    ("S5-负向", "N16"): "NOTSPECIALTY:妇科",
    ("S5-负向", "N17"): "NOTSPECIALTY:男科",
    ("S5-负向", "N18"): "NOTSPECIALTY:小儿感染科",
    ("S5-负向", "N19"): "ERROR:1105",
    ("S5-负向", "N20"): "ERROR:1123",
    ("S5-负向", "N2-空或诉求"): "STATUS:NEED_DESCRIPTION",
    ("S10-含糊-N2", "VAGUE-FIRST"): "STATUS:NEED_DESCRIPTION",
    ("S10-含糊-N2", "VAGUE-SECOND"): "STATUS:NEED_SLOT",
    ("S10-含糊-N2", "VAGUE-SECOND-CLEAR"): "STATUS:NEED_SLOT",
    ("S16-停用科室", "N14"): "NOTDEPT:骨科",
    ("S9-槽位-N3", "SLOT-BIND"): "STATUS:CARD_READY;NOSLOT",
}

BY_INPUT = {
    ("S9-槽位-N3", "男，干咳三天"): "SLOT:AGE",
    ("S9-槽位-N3", "女，28岁，头痛"): "SLOT:DURATION",
    ("S9-槽位-N3", "女，32岁，干咳半年"): "NOSLOT;STATUS:CARD_READY",
    ("S9-槽位-N3", "32岁，干咳三天"): "SLOT:GENDER",
    ("S9-槽位-N3", "干咳三天"): "SLOT:GENDER",
    ("S9-槽位-N3", "女，32岁，干咳"): "SLOT:DURATION",
    ("S9-槽位-N3", "女，1岁，咳嗽三天"): "NOSLOT;SPECIALTY:小儿呼吸科",
    ("S9-槽位-N3", "女，120岁，咳嗽三天"): "NOSLOT;SPECIALTY:呼吸内科",
    ("S9-槽位-N3", "女，0岁，咳嗽三天"): "SLOT:AGE",
    ("S9-槽位-N3", "女，121岁，咳嗽三天"): "SLOT:AGE",
    ("S9-槽位-N3", "女，三岁，咳嗽三天"): "SLOT:AGE",
    ("S9-槽位-N3", "女，32岁，疼了三"): "SLOT:DURATION",
    ("S9-槽位-N3", "男女都有这种情况，咳嗽三天"): "SLOT:AGE",
    ("S9-槽位-N3", "女男都一样，咳嗽三天"): "SLOT:AGE",
    ("S11-归约-N4", "高血压，头晕三天"): "REASON:CANON_TERM",
}


def assert_for(suite: str, case_ref: str, text: str) -> str:
    """返回该行的规格断言 token；空串表示不断言。"""
    if (suite, case_ref) in BY_REF:
        return BY_REF[(suite, case_ref)]
    if (suite, text) in BY_INPUT:
        return BY_INPUT[(suite, text)]
    if suite == "S6-安全-BASELINE":
        base = case_ref.split("@")[0]
        return "STATUS:URGENT" if base.startswith("URG") else "STATUS:NEED_HUMAN"
    if suite == "S7-安全-DRAFT影子":
        base = case_ref.split("@")[0]
        if case_ref.endswith("@ENFORCE"):
            return "STATUS:URGENT" if base.startswith("URG") else "SAFETY:UNSUITABLE"
        return f"STATUS:PASS;SHADOW:{base}"
    if suite == "S8-安全-负向":
        # URGENT BASELINE 的否定/不确定策略都是 ESCALATE，连「没有胸痛」也拦；转人工是 SUPPRESS
        if case_ref.startswith("NEG-转人工"):
            return "SAFETY:PASS"
        if case_ref.startswith("RES-"):
            word = case_ref[4:]
            if word in ("胸痛", "呼吸困难", "意识障碍", "口唇发紫"):
                return "STATUS:URGENT"
            return "STATUS:NEED_HUMAN"
        if case_ref.startswith("NEG-"):
            word = case_ref[4:]
            return "STATUS:URGENT" if word in ("胸痛", "呼吸困难", "意识障碍", "口唇发紫") \
                else "SAFETY:PASS"
        return "SAFETY:PASS"
    if suite in ("S13-确认-N7", "S14-派医生-N8", "S15-占坑-N9", "S29-多轮-确认取消"):
        return ""
    if suite == "S31-安全红旗":
        if case_ref.startswith("OFF") or case_ref in ("PAD", "OFF-CATALOG"):
            return ""
        base = case_ref.split("@")[0].replace("-TURN2", "")
        if case_ref.endswith("@ENFORCE"):
            if base.startswith("UNSUITABLE"):
                return "SAFETY:UNSUITABLE"
            return "STATUS:URGENT" if base.startswith("URG") else "STATUS:NEED_HUMAN"
        if base.startswith("URG-BASE"):
            return "STATUS:URGENT"
        if base.startswith("HUMAN-BASE"):
            return "STATUS:NEED_HUMAN"
        if base.startswith("URG-DRAFT") or base.startswith("UNSUITABLE"):
            return "SAFETY:PASS"
        return ""
    if suite == "S26-多轮-补槽" and case_ref and "对照" not in (text or ""):
        return ""
    return ""


def evaluate(token: str, row: dict) -> bool:
    """按 token 校验一行；没断言算通过。"""
    if not token:
        return True
    for item in token.split(";"):
        if _one(item, row):
            return True
    return False


def _one(item: str, row: dict) -> bool:
    key, _, value = item.partition(":")
    if key == "SPECIALTY":
        return row["expect_specialty"] == value
    if key == "NOTSPECIALTY":
        return row["expect_specialty"] != value
    if key == "NOTDEPT":
        return row["expect_department"] != value
    if key == "DEPT":
        return row["expect_department"] == value
    if key == "STATUS":
        return row["expect_status_code"] == str(STATUS_CODE.get(value, -1))
    if key == "SAFETY":
        return row["expect_safety_status"] == value
    if key == "SHADOW":
        return value in [v for v in row["expect_shadow_rules"].split("|") if v]
    if key == "SLOT":
        return row["expect_missing_slot"] == value
    if key == "NOSLOT":
        return row["expect_missing_slot"] == ""
    if key == "REASON":
        return row["expect_route_reason_rule"] == value
    if key == "FINAL":
        return row["expect_route_reason"] == value
    if key == "ERROR":
        return row["expect_error_code"] == value
    return True
