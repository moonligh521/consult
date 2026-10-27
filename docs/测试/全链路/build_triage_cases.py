# -*- coding: utf-8 -*-
"""生成挂号全链路测试数据集（N1 → N9）。

产出：同目录 `triage-cases.csv`（≥10 万条）。
每行是一条用例：一套输入 + 复刻器算出的全链路期望 + 业务金标。

列的含义、集合口径、怎么跑，见 `全链路测试数据集.md`。
跑法：python docs/测试/全链路/build_triage_cases.py
"""
from __future__ import annotations

import csv
import json
import pathlib
import random
import sys
import unicodedata
from typing import Dict, List, Optional, Sequence

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from gold_137 import GOLD_UTTERANCES, as_variants  # noqa: E402
from gold_assert import assert_for  # noqa: E402
from rule_replica_v2 import (  # noqa: E402
    Replica, Snapshot, URGENT_JSON, load_catalog, load_safety,
)

OUT_CSV = HERE / "triage-cases.csv"

COLUMNS = [
    "case_id", "suite", "node", "case_ref", "action", "turn", "scenario",
    "input", "prior_input", "session_gender", "session_age",
    "slot_gender", "slot_age", "slot_duration",
    "expect_status_code", "expect_status_name", "expect_missing_slot",
    "expect_safety_status", "expect_safety_rules", "expect_shadow_rules",
    "expect_canon_reason", "expect_canon_source", "expect_terms", "expect_symptoms",
    "expect_department_code", "expect_department", "expect_specialty", "expect_evidence",
    "expect_route_reason", "expect_route_source", "expect_route_certainty", "expect_candidates",
    "expect_route_reason_rule",
    "expect_error_code", "spec_assert",
    "gold_department", "gold_specialty", "gold_hit", "countable",
    "variant_slot", "variant_tone", "variant_noise", "remark",
]

STATUS_NAME = {
    1: "待描述", 2: "待补槽", 3: "急症分流", 4: "卡片待确认",
    5: "已确认", 6: "需要人工", 7: "安全兜底", 8: "渠道不适用",
}
GENDER_CN = {1: "男", 2: "女"}
SAFETY_NAME = {3: "URGENT", 6: "NEED_HUMAN", 7: "SAFE_FALLBACK", 8: "UNSUITABLE", None: "PASS"}

TYPO_MAP = {
    "咳嗽": "刻嗽", "头痛": "头通", "腹痛": "腹疼", "腹泻": "腹泄", "胃痛": "胃疼",
    "失眠": "失眼", "哮喘": "孝喘", "呕吐": "藕吐", "头晕": "头运", "恶心": "饿心",
}
FILLERS = ["呃", "就是那个", "怎么说呢", "嗯"]
PREFIXES = ["你好，", "请问，", "医生您好，", "麻烦问一下，", "打扰了，"]


# ---------------------------------------------------------------- 工具
def _spec_assert(suite: str, case_ref: str, text: str, gold_specialty: str,
                 countable: bool) -> str:
    """行级规格断言：S1/S2 用金标二级科，其余查 gold_assert 表。"""
    if suite in ("S1-正向-二级科词条", "S2-规格金标137"):
        return f"SPECIALTY:{gold_specialty}" if (countable and gold_specialty) else ""
    return assert_for(suite, case_ref, text)


def _card_assert(action: str, scenario: str, error_code: str, specialty: str) -> str:
    """N7–N9 行的规格断言。有错误码先断言错误码。"""
    if error_code:
        return f"ERROR:{error_code}"
    if scenario == "CONFIRM-AS-IS":
        return "STATUS:CONFIRMED"
    if scenario.startswith("PATCH-SPEC-"):
        return f"SPECIALTY:{specialty}" if specialty else ""
    if scenario == "PATCH-DESC-转人工":
        return "STATUS:NEED_HUMAN"
    if action in ("DISPATCH", "ORDER"):
        return "STATUS:CONFIRMED"
    return ""


def compact(text: str) -> str:
    return "".join(ch for ch in unicodedata.normalize("NFKC", text or "") if not ch.isspace())


def join(values: Optional[Sequence[str]]) -> str:
    return "|".join(v for v in (values or []) if v)


# ---------------------------------------------------------------- 核心词条
def build_cores(snap: Snapshot) -> List[dict]:
    """每个二级科的每条挂词 / 病名 / 一级名构成一个「核心」。"""
    cores: List[dict] = []
    seen = set()
    for dept in snap.enabled:
        base_gender = 1 if dept.male_only() else (2 if dept.female_only() else 2)
        base_age = 8 if dept.pediatric_catch() else 32
        for spec in dept.specialties:
            for kind, term in [("keyword", t) for t in spec.match_terms()] + \
                              [("disease", d) for d in spec.diseases]:
                if not term or term in seen:
                    continue
                seen.add(term)
                cores.append({
                    "term": term, "kind": kind, "dept_code": dept.code, "dept": dept.name,
                    "spec": spec.name, "gender": base_gender, "age": base_age,
                })
        for kind, term in [("deptword", k) for k in dept.routing_keywords] + [("deptname", dept.name)]:
            if not term or term in seen:
                continue
            seen.add(term)
            cores.append({
                "term": term, "kind": kind, "dept_code": dept.code, "dept": dept.name,
                "spec": dept.default_specialty(), "gender": base_gender, "age": base_age,
            })
    return cores


# ---------------------------------------------------------------- 话术包装
SLOT_PACKAGES: Dict[str, dict] = {
    "SLOT01-全槽-女32三天": {"fmt": "{g}，{a}岁，{core}{d}", "g": "女", "a": "32", "d": "三天", "explicit": False},
    "SLOT02-全槽-男35一周": {"fmt": "{g}，{a}岁，{core}{d}", "g": "男", "a": "35", "d": "一周", "explicit": False},
    "SLOT03-全槽-女28半天": {"fmt": "{g}，{a}岁，{core}{d}", "g": "女", "a": "28", "d": "半天", "explicit": False},
    "SLOT04-全槽-女70半年": {"fmt": "{g}，{a}岁，{core}{d}", "g": "女", "a": "70", "d": "半年", "explicit": False},
    "SLOT05-全槽-女13三天": {"fmt": "{g}，{a}岁，{core}{d}", "g": "女", "a": "13", "d": "三天", "explicit": False},
    "SLOT06-全槽-女14三天": {"fmt": "{g}，{a}岁，{core}{d}", "g": "女", "a": "14", "d": "三天", "explicit": False},
    "SLOT07-全槽-女15三天": {"fmt": "{g}，{a}岁，{core}{d}", "g": "女", "a": "15", "d": "三天", "explicit": False},
    "SLOT08-全槽-女1三天": {"fmt": "{g}，{a}岁，{core}{d}", "g": "女", "a": "1", "d": "三天", "explicit": False},
    "SLOT09-全槽-女120三天": {"fmt": "{g}，{a}岁，{core}{d}", "g": "女", "a": "120", "d": "三天", "explicit": False},
    "SLOT10-全槽-男8三天": {"fmt": "{g}，{a}岁，{core}{d}", "g": "男", "a": "8", "d": "三天", "explicit": False},
    "SLOT11-缺性别年龄": {"fmt": "{core}{d}", "g": "", "a": "", "d": "三天", "explicit": False},
    "SLOT12-缺年龄": {"fmt": "{g}，{core}{d}", "g": "女", "a": "", "d": "三天", "explicit": False},
    "SLOT13-缺病程": {"fmt": "{g}，{a}岁，{core}", "g": "女", "a": "32", "d": "", "explicit": False},
    "SLOT14-绑就诊人": {"fmt": "{core}{d}", "g": "女", "a": "32", "d": "三天", "explicit": True},
    "SLOT15-非法年龄0": {"fmt": "{g}，{a}岁，{core}{d}", "g": "女", "a": "0", "d": "三天", "explicit": False},
    "SLOT16-非法年龄121": {"fmt": "{g}，{a}岁，{core}{d}", "g": "女", "a": "121", "d": "三天", "explicit": False},
    "SLOT17-病程无单位": {"fmt": "{g}，{a}岁，{core}了三", "g": "女", "a": "32", "d": "", "explicit": False},
    "SLOT18-汉字病程三十天": {"fmt": "{g}，{a}岁，{core}三十天", "g": "女", "a": "32", "d": "三十天", "explicit": False},
}

# 中性槽位：不含儿童年龄、跨性别、缺槽、非法年龄，结论不应因这层包装而改变
NEUTRAL_SLOTS = {
    "SLOT01-全槽-女32三天", "SLOT03-全槽-女28半天", "SLOT04-全槽-女70半年",
    "SLOT09-全槽-女120三天",
}

DURATION_CHIPS = ["半天", "1天", "2天", "3天", "5天", "一周", "半个月", "一个月", "半年", "一年"]
SLOT_CHIP = {"fmt": "{g}，{a}岁，{core}{d}", "g": "女", "a": "32", "d": "", "explicit": False}

TONE_NEUTRAL = [
    "TONE00-裸述", "TONE01-带前缀你好", "TONE02-挂号口吻", "TONE03-礼貌咨询",
    "TONE04-时间线", "TONE05-补述", "TONE06-疑问求助", "TONE07-自述加重",
]
TONE_PERTURBING = [
    "TONE50-儿童标记", "TONE51-想看中医", "TONE52-代述家属", "TONE53-不确定标记",
    "TONE54-多主诉跨科", "TONE55-否定口吻",
]
TONE_STRUCTURE = [
    ("TONE00-裸述", "{core}"),
    ("TONE01-带前缀你好", "你好，{core}"),
    ("TONE02-挂号口吻", "想挂个科，{core}"),
    ("TONE03-礼貌咨询", "请问{core}是怎么回事"),
    ("TONE04-时间线", "这两天{core}"),
    ("TONE05-补述", "除了{nth}，主要是{core}"),
    ("TONE06-疑问求助", "{core}，您说要紧吗"),
    ("TONE07-自述加重", "{core}，越来越重了"),
    ("TONE50-儿童标记", "孩子{core}"),
    ("TONE51-想看中医", "{core}，想看中医"),
    ("TONE52-代述家属", "我妈{core}"),
    ("TONE53-不确定标记", "{core}，不知道挂哪个科"),
    ("TONE54-多主诉跨科", "{core}，还有{nth}"),
    ("TONE55-否定口吻", "没有{nth}，就是{core}"),
]

NOISES = [
    ("NOISE00-无", lambda text: text),
    ("NOISE01-字间空格", lambda text: " ".join(text)),
    ("NOISE02-全角空格", lambda text: "　".join(text)),
    ("NOISE03-表内错字", lambda text: _typo_in(text)),
    ("NOISE04-表外错字", lambda text: _typo_out(text)),
    ("NOISE05-结巴重复", lambda text: _stutter(text)),
    ("NOISE06-语气填充", lambda text: _filler(text)),
    ("NOISE07-标点轰炸", lambda text: f"{text}！！！？"),
    ("NOISE08-冗长前缀", lambda text: f"我这情况是这样的，大概三天前开始的吧，然后一直没好，{text}"),
    ("NOISE09-无标点连写", lambda text: text.replace("，", "")),
    ("NOISE10-英文夹杂", lambda text: f"{text}，suddenly worse"),
]


def _typo_in(text: str) -> str:
    for right, wrong in TYPO_MAP.items():
        if right in text:
            return text.replace(right, wrong)
    return text + "（错字无关）"


def _typo_out(text: str) -> str:
    if not text:
        return text
    rnd = random.Random(_seed_of(text) ^ 0x5A5A)
    body = [ch for ch in text if ch.isprintable()]
    idx = rnd.randrange(len(body))
    body[idx] = "囧"
    return "".join(body)


def _stutter(text: str) -> str:
    return f"{text[:2]}{text}" if len(text) > 2 else text * 2


def _filler(text: str) -> str:
    return f"{FILLERS[_seed_of(text) % len(FILLERS)]}，{text}"


def _seed_of(text: str) -> int:
    return sum(ord(ch) * (i + 7) for i, ch in enumerate(text)) & 0xFFFF


# ---------------------------------------------------------------- 行构造
class Builder:
    def __init__(self, replica: Replica):
        self.rep = replica
        self.snap = replica.snapshot
        self.rows: List[dict] = []
        self.seen_input: Dict[str, str] = {}
        self.weak_cores: List[tuple] = []
        self.seq = 0

    def add(self, *, suite: str, node: str, case_ref: str, text: str,
            gender: Optional[int] = None, age: Optional[int] = None,
            prior: Optional[Sequence[str]] = None, turns: int = 1,
            action: str = "MESSAGE", scenario: str = "",
            gold_department: str = "", gold_specialty: str = "", countable: bool = False,
            variant_slot: str = "", variant_tone: str = "", variant_noise: str = "",
            error_code: str = "", remark: str = "", enforce: Optional[bool] = None,
            alt=None) -> Optional[dict]:
        """跑一次复刻器，落一行。同一个 input 只保留第一次出现的那条。"""
        text = text or ""
        key = (suite, action, compact(text), gender, age, tuple(prior or ()), scenario)
        if key in self.seen_input:
            return None
        repl = self.rep
        if alt is not None:
            result = alt.run(text, gender=gender, age=age, turns=turns, prior_messages=prior)
        elif enforce is not None and enforce != self.rep.enforce_draft:
            other = load_safety(enforce_draft=enforce)
            result = Replica(enforce_draft=enforce, snapshot=self.snap, safety=other).run(
                text, gender=gender, age=age, turns=turns, prior_messages=prior)
        else:
            result = repl.run(text, gender=gender, age=age, turns=turns, prior_messages=prior)
        self.seen_input[key] = suite
        self.seq += 1

        route_info = result["route"]
        slots = result["slots"]
        gold_hit = ""
        if countable and route_info["specialty"] == gold_specialty and result["status_code"] == 4:
            gold_hit = "1"
        elif countable:
            gold_hit = "0"
        row = {
            "case_id": f"TC{self.seq:06d}",
            "suite": suite,
            "node": node,
            "case_ref": case_ref,
            "action": action,
            "turn": turns,
            "scenario": scenario,
            "input": text,
            "prior_input": join(prior),
            "session_gender": GENDER_CN.get(gender, "") if gender else "",
            "session_age": "" if age is None else age,
            "slot_gender": GENDER_CN.get(slots["gender"], ""),
            "slot_age": "" if slots["age"] is None else slots["age"],
            "slot_duration": slots["duration"] or "",
            "expect_status_code": result["status_code"],
            "expect_status_name": result["status_name"],
            "expect_missing_slot": slots["missing"] or "",
            "expect_safety_status": SAFETY_NAME.get(result["safety"]["status_code"], "PASS"),
            "expect_safety_rules": join(result["safety"]["matched_rules"]),
            "expect_shadow_rules": join(result["safety"]["shadow_rules"]),
            "expect_canon_reason": result["canon"]["final_reason"],
            "expect_canon_source": result["canon"]["final_source"],
            "expect_terms": join(result["canon"]["terms"]),
            "expect_symptoms": join(result["symptoms"]),
            "expect_department_code": route_info["department_code"] or "",
            "expect_department": route_info["department"] or "",
            "expect_specialty": route_info["specialty"] or "",
            "expect_evidence": route_info["evidence"] or "",
            "expect_route_reason": route_info["reason"],
            "expect_route_source": route_info["source"],
            "expect_route_certainty": route_info["certainty"],
            "expect_candidates": join(route_info["candidates"]),
            "expect_route_reason_rule": result["route_rule_first_pass"]["reason"],
            "expect_error_code": error_code,
            "spec_assert": _spec_assert(suite, case_ref, text, gold_specialty, countable),
            "gold_department": gold_department,
            "gold_specialty": gold_specialty,
            "gold_hit": gold_hit,
            "countable": "1" if countable else "0",
            "variant_slot": variant_slot,
            "variant_tone": variant_tone,
            "variant_noise": variant_noise,
            "remark": remark,
        }
        self.rows.append(row)
        return row

    # ---------------- 集合生成
    def suite_core_bulk(self, cores: List[dict], target_total: int) -> None:
        """S1 主力：核心词条 × 槽位 × 口吻 × 噪声。"""
        rnd = random.Random(20261023)
        for core in cores:
            target_spec = core["spec"]
            target_dept = core["dept"]
            # 基线：全槽标准 + 裸述 + 无噪声
            base_text = _render_slot(SLOT_PACKAGES["SLOT01-全槽-女32三天"], core["term"], core, None)
            base_gender, base_age = _slot_supply(SLOT_PACKAGES["SLOT01-全槽-女32三天"], core)
            base = self.rep.run(base_text, gender=base_gender, age=base_age)
            anchored = base["route"]["specialty"] == target_spec and base["status_code"] == 4
            row_start = len(self.rows)
            if not anchored:
                self.weak_cores.append((target_dept, target_spec, core["term"], core["kind"],
                                        base["route"]["specialty"], base["route"]["reason"]))
            self.add(
                suite="S1-正向-二级科词条", node="N6", case_ref=f"{target_dept}/{target_spec}",
                text=base_text, gender=base_gender, age=base_age,
                gold_department=target_dept, gold_specialty=target_spec, countable=anchored,
                variant_slot="SLOT01-全槽-女32三天", variant_tone="TONE00-裸述", variant_noise="NOISE00-无",
                remark=f"金标基线 {core['kind']}「{core['term']}」")
            # ① 每个槽位包 × 裸述全覆盖
            for name, pkg in SLOT_PACKAGES.items():
                text = _render_slot(pkg, core["term"], core, None)
                gender, age = _slot_supply(pkg, core)
                countable = name in NEUTRAL_SLOTS
                self.add(
                    suite="S1-正向-二级科词条", node="N3" if not countable else "N6",
                    case_ref=f"{target_dept}/{target_spec}", text=text, gender=gender, age=age,
                    gold_department=target_dept, gold_specialty=target_spec, countable=countable and anchored,
                    variant_slot=name, variant_tone="TONE00-裸述", variant_noise="NOISE00-无",
                    remark="槽位变体")
            # ② 病程芯片
            for chip in DURATION_CHIPS:
                text = _render_slot(SLOT_CHIP, core["term"], core, chip)
                gender, age = _slot_supply(SLOT_CHIP, core)
                self.add(
                    suite="S1-正向-二级科词条", node="N3", case_ref=f"{target_dept}/{target_spec}",
                    text=text, gender=gender, age=age,
                    gold_department=target_dept, gold_specialty=target_spec, countable=anchored,
                    variant_slot=f"SLOT18-病程芯片-{chip}", variant_tone="TONE00-裸述",
                    variant_noise="NOISE00-无", remark="点选病程仍槽齐")
            # ③ 每个口吻 × 全槽
            for title, pattern in TONE_STRUCTURE:
                onset = TONE_ONSET.get(title, "头晕")
                piece = pattern.format(core=core["term"], nth=onset)
                text = _render_slot(SLOT_PACKAGES["SLOT01-全槽-女32三天"], piece, core, None)
                gender, age = _slot_supply(SLOT_PACKAGES["SLOT01-全槽-女32三天"], core)
                neutral = title in TONE_NEUTRAL
                self.add(
                    suite="S1-正向-二级科词条", node="N6" if neutral else "N2",
                    case_ref=f"{target_dept}/{target_spec}", text=text, gender=gender, age=age,
                    gold_department=target_dept if neutral else "",
                    gold_specialty=target_spec if neutral else "", countable=neutral and anchored,
                    variant_slot="SLOT01-全槽-女32三天", variant_tone=title, variant_noise="NOISE00-无",
                    remark="口吻包装" + ("" if neutral else "（有扰动，不计入金标分母）"))
            # ④ 每个噪声 × 全槽
            for title, fn in NOISES:
                piece = fn(core["term"])
                text = _render_slot(SLOT_PACKAGES["SLOT01-全槽-女32三天"], piece, core, None)
                gender, age = _slot_supply(SLOT_PACKAGES["SLOT01-全槽-女32三天"], core)
                neutral = title in ("NOISE00-无", "NOISE01-字间空格", "NOISE02-全角空格",
                                    "NOISE03-表内错字", "NOISE07-标点轰炸", "NOISE09-无标点连写")
                self.add(
                    suite="S1-正向-二级科词条", node="N6" if neutral else "N4",
                    case_ref=f"{target_dept}/{target_spec}", text=text, gender=gender, age=age,
                    gold_department=target_dept if neutral else "",
                    gold_specialty=target_spec if neutral else "", countable=neutral and anchored,
                    variant_slot="SLOT01-全槽-女32三天", variant_tone="TONE00-裸述", variant_noise=title,
                    remark="噪声注入" + ("" if neutral else "（错字/假英文，允许不中）"))
            # ⑤ 随机组合补足
            for _ in range(46):
                pkg_name = rnd.choice(list(SLOT_PACKAGES))
                pkg = SLOT_PACKAGES[pkg_name]
                tone_title, tone_pattern = rnd.choice(TONE_STRUCTURE)
                noise_title, noise_fn = rnd.choice(NOISES)
                piece = noise_fn(tone_pattern.format(core=core["term"], nth=TONE_ONSET.get(tone_title, "头晕")))
                chip = None if pkg_name != "SLOT18-病程芯片" else rnd.choice(DURATION_CHIPS)
                text = _render_slot(pkg, piece, core, chip)
                gender, age = _slot_supply(pkg, core)
                neutral = tone_title in TONE_NEUTRAL and noise_title in (
                    "NOISE00-无", "NOISE01-字间空格", "NOISE03-表内错字", "NOISE07-标点轰炸") \
                    and pkg_name in NEUTRAL_SLOTS
                self.add(
                    suite="S1-正向-二级科词条", node="N6" if neutral else "N4",
                    case_ref=f"{target_dept}/{target_spec}", text=text, gender=gender, age=age,
                    gold_department=target_dept if neutral else "",
                    gold_specialty=target_spec if neutral else "", countable=neutral and anchored,
                    variant_slot=pkg_name, variant_tone=tone_title, variant_noise=noise_title,
                    remark="组合采样")
            if not anchored:
                actual = base["route"]["specialty"]
                for row in self.rows[row_start:]:
                    row["remark"] = f"[非锚点词条→现网{actual}] " + row["remark"]
                    row["countable"] = "0"
            if len(self.rows) >= target_total:
                return

    def suite_gold_137(self) -> None:
        """S2 规格金标：137 个二级科各一条（规格第三节的输入模板）。"""
        missing = []
        unresolved = []
        for dept in self.snap.enabled:
            for spec in dept.specialties:
                name = spec.name
                entries = GOLD_UTTERANCES.get(name)
                if entries is None:
                    # 规格没点名的二级科：用它的挂词兜一条（目录自证）
                    term = spec.match_terms()[0] if spec.match_terms() else name
                    entries = [term, 1 if dept.male_only() else 2, 32]
                    missing.append(f"{dept.name}/{name}（用挂词「{term}」自证）")
                for core, gender, age in as_variants(entries):
                    if dept.male_only():
                        gender = 1
                    if dept.female_only():
                        gender = 2
                    gender_cn = "男" if gender == 1 else "女"
                    for slot_name in ("SLOT01-全槽-女32三天", "SLOT03-全槽-女28半天",
                                      "SLOT04-全槽-女70半年"):
                        pkg = SLOT_PACKAGES[slot_name]
                        use_age = pkg["a"] if (pkg["a"] and "岁" not in str(pkg["a"])
                                               and int(pkg["a"]) >= 14) else str(age or 32)
                        if age and age < 14:
                            use_age = str(age)
                        text = pkg["fmt"].format(g=gender_cn, a=use_age, core=core, d=pkg["d"])
                        self.add(
                            suite="S2-规格金标137", node="N6", case_ref=f"{dept.name}/{name}",
                            text=text, gold_department=dept.name, gold_specialty=name,
                            countable=True, variant_slot=slot_name,
                            variant_tone="TONE00-裸述", variant_noise="NOISE00-无",
                            remark=f"规格金标「{core}」→ {dept.name}/{name}")
                    # 同一主诉的绑就诊人口径：槽位不在原话里
                    self.add(
                        suite="S2-规格金标137", node="N6", case_ref=f"{dept.name}/{name}",
                        text=f"{core}三天", gender=gender, age=age,
                        gold_department=dept.name, gold_specialty=name, countable=True,
                        variant_slot="SLOT14-绑就诊人", variant_tone="TONE00-裸述",
                        variant_noise="NOISE00-无",
                        remark=f"规格金标（绑就诊人补槽）「{core}」→ {dept.name}/{name}")
        covered = {row["gold_specialty"] for row in self.rows if row["suite"] == "S2-规格金标137"}
        lacks = sorted(set(self.snap.enabled_specialty_names()) - covered)
        if lacks:
            unresolved.append(f"二级科没铺到：{lacks}")
        print(f"S2 规格金标：覆盖 {len(covered)}/137"
              + (f"；缺：{unresolved}" if unresolved else "，全覆盖 ✅")
              + (f"；未被规格点名、走目录自证 {len(missing)} 个" if missing else ""))

    def suite_safety_baseline(self, rules_json: List[dict]) -> None:
        """S6 安全 BASELINE：该停的一定停。"""
        tones = ["{w}", "{w}，怎么办", "我有{w}", "请问{w}怎么回事", "{w}两天了", "医生，{w}"]
        for rule in rules_json:
            if rule.get("status") != "BASELINE":
                continue
            for alias in rule.get("aliases", []):
                for pattern in tones:
                    text = pattern.format(w=alias)
                    self.add(
                        suite="S6-安全-BASELINE", node="N1", case_ref=rule["ruleId"],
                        text=text, gold_department="", gold_specialty="", countable=False,
                        remark=f"BASELINE 必须停：{rule['disposition']}")

    def suite_safety_draft(self, rules_json: List[dict]) -> None:
        """S7 DRAFT：SHADOW 下必须仍能挂号，且影子规则要命中。"""
        tones = ["{w}", "{w}两天了", "我有{w}", "{w}，要紧吗", "医生，{w}"]
        for rule in rules_json:
            if rule.get("status") != "DRAFT":
                continue
            for alias in rule.get("aliases", []):
                for pattern in tones:
                    text = pattern.format(w=alias)
                    self.add(
                        suite="S7-安全-DRAFT影子", node="N1", case_ref=rule["ruleId"],
                        text=text, countable=False,
                        remark="SHADOW 下只记影子，不得挡挂号")
                    self.add(
                        suite="S7-安全-DRAFT影子", node="N1", case_ref=rule["ruleId"] + "@ENFORCE",
                        text=text, scenario="ENFORCE", countable=False, enforce=True,
                        remark="ENFORCE 时这条应变停止")

    def suite_safety_negative(self) -> None:
        """S8 安全负向：不该停的必须继续挂号。"""
        texts = [
            "干咳半年", "不要转人工，干咳半年", "人工智能导诊，干咳半年", "有点不舒服",
            "转人工不是转人", "客服还不够快", "没有转人工的必要", "我要找人陪诊不是转人工",
            "没有胸痛", "好像血量不够", "人工 hooked", "挂号不用转人工",
            "手动可是机器", "工人受伤了",
        ]
        negations = [(None, p) for p in ["没有{w}", "不是{w}", "否认{w}", "好像{w}",
                                         "不确定是不是{w}", "可能{w}"]] \
            + [("RES", p) for p in ["已经缓解了，{w}", "现在没事了，{w}", "不疼了，{w}"]]
        words = ["胸痛", "呼吸困难", "意识障碍", "口唇发紫", "转人工"]
        for word in words:
            for kind, pattern in negations:
                ref = (f"RES-{word}" if kind else f"NEG-{word}")
                note = "已缓解口径：resolvedPolicy=ESCALATE 仍会停止" if kind \
                    else "否定/不确定 → 不停止"
                self.add(suite="S8-安全-负向", node="N1", case_ref=ref,
                         text=pattern.format(w=word), countable=False, remark=note)
        for text in texts:
            self.add(suite="S8-安全-负向", node="N1", case_ref="NEG-口语", text=text,
                     countable=False, remark="形似转人工但不是")

    def suite_clarity(self) -> None:
        """S10 N2 含糊：该追问 / 第二句放行。"""
        vague_first = ["有点不舒服", "浑身难受", "身体不适", "不太对劲", "不得劲", "生病了",
                       "不太好", "不舒服", "难受", "不适", "不太对", "有点事", "不好受"]
        vague_second = ["还是难受", "也不舒服", "浑身不得劲", "不太好吧", "就是难受"]
        for first in vague_first:
            self.add(suite="S10-含糊-N2", node="N2", case_ref="VAGUE-FIRST", text=first,
                     turns=1, countable=False, remark="第一句含糊 → 追问")
            for second in vague_second:
                self.add(suite="S10-含糊-N2", node="N2", case_ref="VAGUE-SECOND", text=second,
                         turns=2, prior=[first], countable=False,
                         remark="第二句仍含糊 → 放行，不死循环")
                self.add(suite="S10-含糊-N2", node="N3", case_ref="VAGUE-SECOND-CLEAR",
                         text="干咳三天", turns=2, prior=[first], countable=False,
                         remark="第二句变清楚 → 进槽位")
        for length in range(1, 20):
            text = "不" * length + "舒服"
            self.add(suite="S10-含糊-N2", node="N2", case_ref="VAGUE-LEN", text=text,
                     countable=False, remark=f"长度={len(text)}")
        for text in ["有点不舒服，已经三天了", "不舒服，女，32岁", "难受（吃饭胃口差）", "体检异常"]:
            self.add(suite="S10-含糊-N2", node="N2", case_ref="VAGUE-WITH-SLOT", text=text,
                     countable=False, remark="含糊但能抽槽/有症状 → 不按含糊拦截")

    def suite_slots(self) -> None:
        """S9 N3 槽位：性别 → 年龄 → 病程。"""
        combos = [
            ("男，干咳三天", None, None, "缺年龄"),
            ("女，28岁，头痛", None, None, "缺病程"),
            ("女，32岁，干咳半年", None, None, "槽齐出卡"),
            ("32岁，干咳三天", None, None, "缺性别"),
            ("干咳三天", None, None, "缺性别与年龄"),
            ("女，32岁，干咳", None, None, "缺病程"),
            ("女，1岁，咳嗽三天", None, None, "年龄下沿合法"),
            ("女，120岁，咳嗽三天", None, None, "年龄上沿合法"),
            ("女，0岁，咳嗽三天", None, None, "年龄非法→继续问"),
            ("女，121岁，咳嗽三天", None, None, "年龄非法→继续问"),
            ("女，三岁，咳嗽三天", None, None, "汉字年龄抽不出"),
            ("女，32岁，疼了三", None, None, "病程无单位"),
            ("男女都有这种情况，咳嗽三天", None, None, "同句男女取先出现"),
            ("女男都一样，咳嗽三天", None, None, "同句女在前取女"),
        ]
        for text, gender, age, note in combos:
            self.add(suite="S9-槽位-N3", node="N3", case_ref="SLOT-ORDER", text=text,
                     gender=gender, age=age, countable=False, remark=note)
        for chip in DURATION_CHIPS:
            self.add(suite="S9-槽位-N3", node="N3", case_ref="SLOT-CHIP",
                     text=f"女，32岁，干咳{chip}", countable=False, remark="点选病程合法")
        for age in range(0, 130):
            self.add(suite="S9-槽位-N3", node="N3", case_ref="SLOT-AGE-SWEEP",
                     text=f"女，{age}岁，咳嗽三天", countable=False, remark="年龄边界扫描")
        self.add(suite="S9-槽位-N3", node="N3", case_ref="SLOT-BIND", text="干咳三天",
                 gender=2, age=32, countable=False, remark="绑就诊人一次补齐槽位")

    def suite_canon(self) -> None:
        """S11 N4 归约：口语句 → 病名。"""
        cases = [
            ("口渴，喝水多，夜里老跑厕所", "糖尿病三多。DRAFT 未审核，现网按症状词"),
            ("血糖高，乏力", "血糖高 → 糖尿病（DRAFT）"),
            ("血压高，头晕", "血压高 → 高血压（DRAFT）"),
            ("只说头晕", "只有头晕不归高血压"),
            ("血脂高，胖", "血脂高 → 高脂血症（DRAFT）"),
            ("尿酸高，脚疼", "尿酸高 → 痛风（DRAFT）"),
            ("我有高血压病史，现在咳嗽三天", "病史句里的病名进 historyTerms"),
            ("高血压，头晕三天", "病名字面直命中"),
            ("胃炎老毛病犯了", "病史标记"),
            ("以前得过肺炎，这回是支气管的事", "既往病名不应占用当前归约"),
        ]
        for text, note in cases:
            self.add(suite="S11-归约-N4", node="N4", case_ref="CANON", text=text,
                     countable=False, remark=note)
        for text, note in cases:
            self.add(suite="S11-归约-N4", node="N4", case_ref="CANON@REVIEWED",
                     text=text + "（同义词已审核口径）", countable=False, remark=note + " [影射：REVIEWED 后期望]")
        for short in ["疼", "难受", "不舒服", "？？", "abc", "kesou", "kesou 三天", "秃", "油", "疼了一个período"]:
            self.add(suite="S11-归约-N4", node="N4", case_ref="CANON-SHORT", text=short,
                     countable=False, remark="短句/拼音走 NO_HIT 或 TOO_SHORT")

    # ---------------------------------------------------------------- 口语写法
    def suite_slang(self, cores: List[dict]) -> None:
        """S19–S25：真实在线问诊里患者会怎么打字（拼音/英文/错字/数字单位/提问/代述/混写）。"""
        import slang as SL

        rnd = random.Random(20261027)
        pkg = SLOT_PACKAGES["SLOT01-全槽-女32三天"]
        index = {core["term"]: core for core in cores}
        unmatched = []

        def baseline_of(core: dict) -> tuple:
            text = _render_slot(pkg, core["term"], core, None)
            gender, age = _slot_supply(pkg, core)
            res = self.rep.run(text, gender=gender, age=age)
            return text, res

        def emit(suite: str, node: str, case_ref: str, text: str, core: dict,
                 gold: tuple, family: str, surface: str, countable: bool,
                 dur: str = "", remark: str = ""):
            return self.add(suite=suite, node=node, case_ref=case_ref, text=text,
                            gold_department=gold[0], gold_specialty=gold[1], countable=countable,
                            variant_slot="SLOT01-全槽-女32三天", variant_tone=family,
                            variant_noise=surface if not dur else f"{surface}/{dur}",
                            remark=remark or family)

        def pseudo_core(term: str, spec_name: str) -> Optional[dict]:
            """目录里查不到的说法，按 MANUAL_GOLD 造一个「应该挂到哪」的伪核心。"""
            for dept in self.snap.enabled:
                for spec in dept.specialties:
                    if spec.name == spec_name:
                        return {"term": term, "kind": "slang", "dept_code": dept.code,
                                "dept": dept.name, "spec": spec_name,
                                "gender": 1 if dept.male_only() else 2,
                                "age": 8 if dept.pediatric_catch() else 32}
            return None

        gold_cache: Dict[str, tuple] = {}

        def gold_of(core: dict) -> tuple:
            cached = gold_cache.get(core["term"])
            if cached is not None:
                return cached
            _, res = baseline_of(core)
            gold = (res["route"]["department"], res["route"]["specialty"]) \
                if res["status_code"] == 4 else ("", "")
            gold_cache[core["term"]] = gold
            return gold

        def anchored(core: dict) -> bool:
            _, res = baseline_of(core)
            return res["status_code"] == 4

        def resolve(canonical: str):
            """返回 (core, gold, 备注)；目录查不到时退回 MANUAL_GOLD。"""
            core = index.get(canonical)
            if core is not None:
                gold = gold_of(core)
                return (core, gold, "") if gold[1] else (None, None, "")
            unmatched.append(canonical)
            spec = SL.MANUAL_GOLD.get(canonical)
            if not spec:
                return None, None, "MISSING"
            core = pseudo_core(canonical, spec)
            if core is None:
                return None, None, "MISSING"
            return core, (core["dept"], core["spec"]), "MANUAL"

        orphan_core = {"term": "", "kind": "orphan", "dept_code": 0, "dept": "", "spec": "",
                       "gender": 2, "age": 32}

        # ① 拼音
        for canonical, forms in SL.PINYIN_FORMS.items():
            core, gold, kind = resolve(canonical)
            if core is None and kind != "MISSING":
                continue
            if core is None:  # 目录里连这个词都没有：照样造出来，只记录现网行为
                core = dict(orphan_core, term=canonical)
                gold = ("", "")
            surfaces = list(forms) + [forms[0].upper(), forms[0].capitalize()]
            for surface in surfaces:
                for title, piece in SL.PINYIN_CARRIERS:
                    text = _render_slot(pkg, piece.format(x=surface), core, None)
                    emit("S19-口语-拼音", "N6", f"{core['dept']}/{core['spec']}", text, core,
                         gold, title, surface, True,
                         remark=f"拼音写法「{surface}」对应「{canonical}」")

        # ② 英文 / 中英夹杂
        for canonical, forms in SL.ENGLISH_FORMS.items():
            core, gold, kind = resolve(canonical)
            if core is None and kind != "MISSING":
                continue
            if core is None:
                core = dict(orphan_core, term=canonical)
                gold = ("", "")
            surfaces = list(forms)
            if forms:
                surfaces = surfaces + [forms[0].upper(), forms[0].capitalize()]
            for surface in surfaces:
                for title, piece in SL.ENGLISH_CARRIERS:
                    text = _render_slot(pkg, piece.format(x=surface), core, None)
                    emit("S20-口语-英文", "N6", f"{core['dept']}/{core['spec']}", text, core,
                         gold, title, surface, True,
                         remark=f"英文写法「{surface}」对应「{canonical}」")

        # ③ 错别字与输入法误操作（对所有核心词条做一遍）
        typo_carriers = [("C00-全槽裸述", "{x}"), ("C01-求挂号", "{x}，挂哪个科")]
        for core in cores:
            gold = gold_of(core)
            if not gold[1]:
                continue
            term = core["term"]
            surfaces = []
            for pick, name in ((0, "TYP01-同音形近"), (1, "TYP02-另一种错法"), (2, "TYP03-再换一种")):
                wrong = SL.confuse_term(term, pick)
                if wrong and wrong != term:
                    surfaces.append((name, wrong))
            for name, wrong in (("TYP04-连按重复", SL.stutter(term)),
                                ("TYP05-漏打一字", SL.drop_char(term)),
                                ("TYP06-繁体输入", SL.to_trad(term)),
                                ("TYP07-表情尾巴", SL.emoji_tail(term)),
                                ("TYP08-疑问口吻", f"{term}?"),
                                ("TYP09-全角符号", f"{term}，？？"),
                                ("TYP10-重复描述", f"{term}，就是{term}")):
                if wrong and wrong != term:
                    surfaces.append((name, wrong))
            for name, wrong in surfaces:
                for carrier_name, carrier in typo_carriers:
                    text = _render_slot(pkg, carrier.format(x=wrong), core, None)
                    emit("S21-口语-错别字", "N6", f"{core['dept']}/{core['spec']}", text, core,
                         gold, f"{name}{carrier_name}", wrong, True,
                         remark=f"错别字写法，原词「{term}」")

        # ④ 病程单位怎么写
        dur_stat: Dict[str, list] = {name: [0, 0] for name, _ in SL.DURATION_FORMS}
        for core in cores[::3]:
            gold = gold_of(core)
            if not gold[1]:
                continue
            for dur_name, dur in SL.DURATION_FORMS:
                countable = not dur_name.startswith("DUR20")
                text = _render_slot(pkg, core["term"], core, dur)
                row = emit("S22-口语-病程写法", "N6", f"{core['dept']}/{core['spec']}", text,
                           core, gold, "DUR-病程写法", core["term"], countable, dur=dur_name,
                           remark=f"病程写成「{dur}」")
                if row:
                    slot_ok = bool(row["slot_duration"])
                    dur_stat[dur_name][0] += 1
                    dur_stat[dur_name][1] += 1 if slot_ok else 0
                    if not slot_ok:
                        row["countable"] = "0"
                        row["gold_department"] = ""
                        row["gold_specialty"] = ""
                        row["gold_hit"] = ""
                        row["node"] = "N3"
                        row["remark"] = f"病程写成「{dur}」抽不出来 → 先补槽"

        # ⑤ 提问句式
        for idx, core in enumerate(cores):
            gold = gold_of(core)
            if not gold[1]:
                continue
            forms = [SL.ASK_FORMS[(idx + step) % len(SL.ASK_FORMS)] for step in (0, 4, 8, 11)]
            for ask_name, ask in forms:
                text = _render_slot(pkg, ask.format(core=core["term"]), core, None)
                emit("S23-口语-提问句式", "N6", f"{core['dept']}/{core['spec']}", text, core,
                     gold, ask_name, ask_name, True, remark="真实开场白句式")

        # ⑥ 家属代述 / 给孩子问
        for core in cores[::2]:
            gold = gold_of(core)
            if not gold[1]:
                continue
            for px_name, px, child in SL.PROXY_FORMS:
                text = _render_slot(pkg, px.format(core=core["term"]), core, None)
                if child:
                    emit("S24-口语-家属代述", "N6", f"{core['dept']}/{core['spec']}", text, core,
                         ("", ""), px_name, px_name, False,
                         remark="儿童代述：应按儿科路径，不校原来的二级科")
                else:
                    emit("S24-口语-家属代述", "N6", f"{core['dept']}/{core['spec']}", text, core,
                         gold, px_name, px_name, True, remark="成人代述：结论不应变")

        # ⑦ 真实聊天混写：随机叠加多种扰动
        ask_pool = SL.ASK_FORMS + [(name, pat) for name, pat, child in SL.PROXY_FORMS if not child]
        mix_rows = 0
        attempts = 0
        while mix_rows < 9_000 and attempts < 60_000:
            attempts += 1
            core = rnd.choice(cores)
            gold = gold_of(core)
            if not gold[1]:
                continue
            term = core["term"]
            piece = term
            applied = []
            for _ in range(rnd.randint(1, 3)):
                kind = rnd.choice(["pinyin", "english", "typo", "trad", "stutter",
                                   "emoji", "space", "drop", "dup"])
                if kind == "pinyin" and term in SL.PINYIN_FORMS:
                    piece = rnd.choice(SL.PINYIN_FORMS[term])
                    applied.append("PINYIN")
                elif kind == "english" and term in SL.ENGLISH_FORMS:
                    piece = rnd.choice(SL.ENGLISH_FORMS[term])
                    applied.append("ENGLISH")
                elif kind == "typo":
                    wrong = SL.confuse_term(piece, rnd.randrange(3))
                    if wrong:
                        piece = wrong
                        applied.append("TYPO")
                elif kind == "trad":
                    wrong = SL.to_trad(piece)
                    if wrong:
                        piece = wrong
                        applied.append("TRAD")
                elif kind == "stutter":
                    piece = SL.stutter(piece)
                    applied.append("STUTTER")
                elif kind == "emoji":
                    piece = SL.emoji_tail(piece)
                    applied.append("EMOJI")
                elif kind == "space":
                    piece = " ".join(piece)
                    applied.append("SPACE")
                elif kind == "drop":
                    piece = SL.drop_char(piece)
                    applied.append("DROP")
                elif kind == "dup":
                    piece = f"{piece}，还有{rnd.choice(['头晕', '乏力', '恶心', '失眠', '牙疼'])}"
                    applied.append("TWOSYM")
            if not piece:
                continue
            carrier_name, carrier = rnd.choice(ask_pool)
            if "{core}" in carrier:
                piece = carrier.format(core=piece)
            if rnd.random() < 0.35:
                piece = f"{rnd.choice(FILLERS)}，{piece}"
            dur_name, dur = rnd.choice(SL.DURATION_FORMS)
            countable = not dur_name.startswith("DUR20")
            text = _render_slot(pkg, piece, core, dur)
            row = emit("S25-口语-混写", "N6", f"{core['dept']}/{core['spec']}", text, core,
                       gold, "+".join(applied) or "PLAIN", carrier_name, countable, dur=dur_name,
                       remark="口语混写随机组合")
            if row:
                mix_rows += 1

        bad_dur = [(name, stat[0] - stat[1], stat[0]) for name, stat in dur_stat.items()
                   if stat[0] and stat[1] < stat[0]]
        if bad_dur:
            print("  ⚠ 抽不出槽的病程写法：")
            for name, bad, total in bad_dur:
                print(f"    {name}  {bad}/{total} 抽不出来 → 会多问一句病程")

        still_missing = sorted({t for t in unmatched if t not in SL.MANUAL_GOLD})
        if still_missing:
            print(f"  ⚠ 还有 {len(still_missing)} 个常用说法在目录里查不到，也没给期望（只记录现网行为）：")
            print("    " + "、".join(still_missing))
        manual = sorted({t for t in unmatched if t in SL.MANUAL_GOLD})
        if manual:
            print(f"  ⚠ {len(manual)} 个常用说法目录没有，按规格手工补了期望：")
            print("    " + "、".join(manual))

    def suite_symptoms(self) -> None:
        """S12 N5 症状标签。"""
        cases = [
            "干咳半年", "胃痛，还吐酸水", "头痛加呕吐", "孩子咳嗽还有点喘",
            "体检报告看不懂", "咳 嗽 三 天", "胸痛两天", "呼吸困难两天",
            "头晕乏力一周", "鼻塞流鼻涕打喷嚏", "好像有点胃炎，也说不清",
        ]
        for text in cases:
            self.add(suite="S12-症状-N5", node="N5", case_ref="SYMPTOM", text=text,
                     countable=False, remark="症状标签必须出现在描述里")
            self.add(suite="S12-症状-N5", node="N5", case_ref="SYMPTOM-FULL",
                     text=f"女，32岁，{text}", countable=False, remark="带齐槽的症状标签")

    def suite_boundary(self) -> None:
        """S3 边界：规则刚好还成立（B1–B20）。"""
        cases = [
            ("B1", "咳嗽还头痛", 2, 32, "呼吸内科", "SINGLE_SPECIALTY，证据咳嗽"),
            ("B2", "头痛加咳嗽", 2, 32, "神经内科", "SINGLE_SPECIALTY，证据头痛"),
            ("B3", "咳 嗽", 2, 32, "呼吸内科", "去空格仍匹配"),
            ("B4", "腹 泻 ， 咳 嗽", 2, 32, "消化内科", "长词优先"),
            ("B5", "咳嗽三天，13岁", 2, 13, "小儿呼吸科", "PEDIATRIC <14"),
            ("B6", "孩子咳嗽三天", 2, 32, "小儿呼吸科", "儿童标记"),
            ("B7", "尿频尿急，女", 2, 32, None, "性别钳制后妇产科侧"),
            ("B8", "尿频尿急，男", 1, 32, None, "性别钳制后男科侧"),
            ("B9", "咳嗽，想看中医", 2, 32, "中医科", "OVERRIDE"),
            ("B10", "想看中医调理失眠", 2, 32, "中医科", "OVERRIDE 覆盖失眠"),
            ("B11", "挂内科", 2, 32, "普内科", "一级名落默认二级"),
            ("B12", "挂皮肤性病科", 2, 32, "皮肤科", "一级名落默认二级"),
            ("B13", "头通两天", 2, 32, "神经内科", "错别字归一"),
            ("B14", "刻嗽三天", 2, 32, "呼吸内科", "错别字归一"),
            ("B15", "血压高头晕", 2, 50, None, "同义词 DRAFT，现网不一定 CANON_TERM"),
            ("B16", "口渴，喝水多，夜里老跑厕所", 2, 40, None, "三多，DRAFT"),
            ("B19", "女，1岁，咳嗽三天", 2, 1, "小儿呼吸科", "年龄下沿"),
            ("B19b", "女，120岁，咳嗽三天", 2, 120, "呼吸内科", "年龄上沿"),
            ("B20", "鼻塞流鼻涕打喷嚏", 2, 32, "耳鼻咽喉科", "同科多词不冲突"),
        ]
        for ref, text, gender, age, gold, note in cases:
            self.add(suite="S3-边界-正向", node="N6", case_ref=ref, text=text,
                     gender=gender, age=age, gold_specialty=gold or "", countable=False,
                     remark=note)
        # B17 出卡后禁对话 / B18 病程芯片轮转
        self.add(suite="S3-边界-正向", node="N6", case_ref="B17", text="夜里也咳",
                 turns=2, prior=["女，32岁，干咳半年"], countable=False,
                 error_code="1104", remark="出卡后禁对话，改信息只走卡片")
        for chip in DURATION_CHIPS:
            self.add(suite="S3-边界-正向", node="N6", case_ref="B18",
                     text=f"女，32岁，干咳{chip}", countable=False, remark="点选病程槽齐出卡，科不变")

    def suite_negative_boundary(self) -> None:
        """S4 负向边界：差一个条件就越界（NB1–NB15）。"""
        cases = [
            ("NB1", "咳嗽三天，14岁", 2, 14, "呼吸内科，不再儿科"),
            ("NB2", "咳嗽，15岁", 2, 15, "呼吸内科"),
            ("NB5", "女，0岁，咳嗽三天", 2, None, "抽不出年龄"),
            ("NB5b", "女，121岁，咳嗽三天", 2, None, "抽不出年龄"),
            ("NB6", "女，32岁，头疼了三", 2, 32, "抽不出病程"),
            ("NB7", "男女都有这个问题，咳嗽三天", None, None, "性别取先出现"),
            ("NB8", "睡觉打呼噜", 2, 32, "耳鼻咽喉科，不是睡眠医学科"),
            ("NB9", "失眠", 2, 32, "睡眠医学科"),
            ("NB9b", "失眠，想看中医", 2, 32, "加中医后 OVERRIDE"),
            ("NB10", "肺结核", 2, 32, "结核病科优先"),
            ("NB11", "腰肌劳损", 2, 32, "骨科不是中医"),
            ("NB12", "血压高", 2, 32, "同义词未审核，不当金标"),
            ("NB13", "胃痛，牙疼，三天", 2, 32, "CONFLICT 且模型关 → 全科 FALLBACK"),
        ]
        for ref, text, gender, age, note in cases:
            self.add(suite="S4-边界-负向", node="N6", case_ref=ref, text=text,
                     gender=gender, age=age, countable=False, remark=note)
        self.add(suite="S4-边界-负向", node="N2", case_ref="NB3", text="有点不舒服",
                 countable=False, remark="模糊词刚够长 ≤16 字 → N2 追问，不出科")
        self.add(suite="S4-边界-负向", node="N2", case_ref="NB4", text="还是不舒服",
                 turns=2, prior=["有点不舒服"], countable=False,
                 remark="第二句仍含糊 → 放行补槽，禁止第三次只追问")
        self.add(suite="S4-边界-负向", node="N2", case_ref="NB4b", text="还是难受",
                 turns=3, prior=["有点不舒服", "还是不舒服"], countable=False,
                 remark="第三句仍含糊 → 也不许一直追问")
        # NB14 改卡到未开通一级 → INVALID_DEPARTMENT(1119)
        self.add(suite="S4-边界-负向", node="N7", case_ref="NB14", action="PATCH",
                 text="干咳半年", scenario="PATCH-DEPT-星际科", countable=False,
                 error_code="1119", remark="改卡到未开通科室 → INVALID_DEPARTMENT")
        self.add(suite="S4-边界-负向", node="N7", case_ref="NB14b", action="PATCH",
                 text="干咳半年", scenario="PATCH-DEPT-皮肤科-关闭", countable=False,
                 error_code="1119", remark="改卡到停用科室 → INVALID_DEPARTMENT")
        # NB15 急症后再补描述仍 URGENT
        self.add(suite="S4-边界-负向", node="N1", case_ref="NB15", text="其实是干咳",
                 turns=2, prior=["胸痛两小时"], countable=False,
                 remark="急症后再发描述，仍 URGENT，不改挂呼吸")

    def suite_negative(self) -> None:
        """S5 负向：不该挂成某科 / 应落全科 / 应 CONFLICT / 应停在安全。"""
        cases = [
            ("N1", "体检报告看不懂", 2, 32, "全科"),
            ("N2", "", 2, 32, "全科或待描述"),
            ("N3", "不知道挂哪个科，头晕", 2, 32, "全科 UNCERTAIN_MARKER"),
            ("N4", "肚子疼，说不清哪个位置", 2, 32, "全科 UNCERTAIN_MARKER"),
            ("N5", "胃痛，牙疼，三天", 2, 32, "CONFLICT，候选含消化内科+全科"),
            ("N6", "咳嗽，腰也疼，不知道挂哪科", 2, 32, "全科"),
            ("N7", "头晕乏力一周", 2, 32, "AMBIGUOUS_ONLY → 全科候选"),
            ("N8", "没有呼息困难", 2, 32, "不应升 120，按全科/继续挂号"),
            ("N9", "人工智能导诊，干咳半年", 2, 32, "出卡，呼吸内科"),
            ("N10", "不要转人工，干咳半年", 2, 32, "出卡，呼吸内科"),
            ("N11", "胸痛两小时", 2, 32, "URGENT 无卡"),
            ("N12", "转人工", 2, 32, "NEED_HUMAN 无卡"),
            ("N13", "快不行了", 2, 32, "NEED_HUMAN 无卡"),
            ("N16", "男，32岁，月经不调三天", 1, 32, "性别钳制后不应走妇科"),
            ("N17", "女，32岁，前列腺炎三天", 2, 32, "不应钳成男科"),
            ("N18", "32岁，手足口病三天", 2, 32, "成人病名无儿童标记，勿误儿科"),
        ]
        for ref, text, gender, age, note in cases:
            self.add(suite="S5-负向", node="N6", case_ref=ref, text=text, gender=gender,
                     age=age, countable=False, remark=note)
        for pattern in [" ", "   ", "\n", "。。。。", "？？？", "hi", "在吗", "帮我挂号",
                        "怎么挂号", "我要拿药", "医保能报吗", "我来复查", "挂什么科", "你好"]:
            self.add(suite="S5-负向", node="N2", case_ref="N2-空或诉求", text=pattern,
                     countable=False, remark="空白/无症状诉求 → 追问或全科")
        # N14 科室停用：骨科 enabled=0
        self.suite_disabled()
        # N15 未开通科名
        self.add(suite="S5-负向", node="N6", case_ref="N15", text="挂星际科", gender=2, age=32,
                 countable=False, remark="编造科名 → 全科")
        self.add(suite="S5-负向", node="N6", case_ref="N15b", text="挂放射科", gender=2, age=32,
                 countable=False, remark="未开通科名 → 全科")
        # N19 / N20 会话状态 independent 行
        self.add(suite="S5-负向", node="N7", case_ref="N19", action="MESSAGE_AFTER_CONFIRM",
                 text="还咳嗽", turns=2, prior=["女，32岁，干咳半年"], countable=False,
                 error_code="1105", remark="已确认后再发普通病情 → ALREADY_CONFIRMED")
        self.add(suite="S5-负向", node="N7", case_ref="N20", action="MESSAGE_AFTER_ENDED",
                 text="还咳嗽", turns=3, prior=["女，32岁，干咳半年"], countable=False,
                 error_code="1123", remark="问诊已结束 → CONSULT_ENDED")

    def suite_disabled(self) -> None:
        """S16 科室停用：该科上看病应该落到全科。"""
        limited = Replica(enforce_draft=False, snapshot=self.snap.without_enabled(5),
                          safety=load_safety())
        keep = self.rep
        texts = ["女，32岁，手腕骨折三天", "腰椎间盘突出", "肩袖损伤", "骨肉瘤", "坐骨神经痛"]
        for text in texts:
            result = limited.run(text, gender=2, age=32)
            key = (compact(text), "DISABLED-ORTHO")
            if key in self.seen_input:
                continue
            self.seen_input[key] = "S16"
            self.seq += 1
            route_info = result["route"]
            self.rows.append({
                **_blank_row(),
                "case_id": f"TC{self.seq:06d}",
                "suite": "S16-停用科室", "node": "N6", "case_ref": "N14", "action": "MESSAGE",
                "turn": 1, "scenario": "骨科 enabled=0", "input": text,
                "prior_input": "", "slot_gender": "女", "slot_age": 32, "slot_duration": "三天",
                "expect_status_code": result["status_code"],
                "expect_status_name": result["status_name"],
                "expect_missing_slot": result["slots"]["missing"] or "",
                "expect_safety_status": SAFETY_NAME.get(result["safety"]["status_code"], "PASS"),
                "expect_safety_rules": join(result["safety"]["matched_rules"]),
                "expect_shadow_rules": join(result["safety"]["shadow_rules"]),
                "expect_canon_reason": result["canon"]["final_reason"],
                "expect_canon_source": result["canon"]["final_source"],
                "expect_terms": join(result["canon"]["terms"]),
                "expect_symptoms": join(result["symptoms"]),
                "expect_department_code": route_info["department_code"] or "",
                "expect_department": route_info["department"] or "",
                "expect_specialty": route_info["specialty"] or "",
                "expect_evidence": route_info["evidence"] or "",
                "expect_route_reason": route_info["reason"],
                "expect_route_source": route_info["source"],
                "expect_route_certainty": route_info["certainty"],
                "expect_candidates": join(route_info["candidates"]),
                "expect_route_reason_rule": result["route_rule_first_pass"]["reason"],
                "spec_assert": "NOTDEPT:骨科",
                "session_gender": "女", "session_age": 32,
                "gold_department": "全科", "gold_specialty": "全科",
                "gold_hit": "1" if route_info["specialty"] == "全科" else "0",
                "countable": "1",
                "variant_slot": "", "variant_tone": "", "variant_noise": "",
                "remark": "停用科在其上的二级不应出卡",
            })
        self.rep = keep

    def suite_reviewed_synonyms(self) -> None:
        """S18 归约-已审核：同义词签核后应走 CANON_TERM 的那批话术。"""
        reviewed_snapshot = self.snap.with_synonyms_reviewed(
            ["SYN-HTN-001", "SYN-HTN-002", "SYN-DM-001", "SYN-DM-002", "SYN-GOUT-001", "SYN-LIP-001"])
        reviewed = Replica(enforce_draft=False, snapshot=reviewed_snapshot, safety=load_safety())
        cases = [
            ("高血压，头晕三天", 2, 50, "普内科", "CANON_TERM"),
            ("最近老是口渴，喝水多，夜里老跑厕所", 2, 40, "内分泌科", "CANON_TERM"),
            ("孩子口渴，喝水多，尿多", 2, 5, None, "PEDIATRIC"),
            ("血压 168，头晕", 2, 60, "普内科", "CANON_TERM"),
            ("血糖偏高，瘦了", 2, 45, "内分泌科", "CANON_TERM"),
            ("尿酸偏高，脚疼", 2, 40, "普内科", "CANON_TERM"),
            ("血脂偏高，头晕", 2, 55, "普内科", "CANON_TERM"),
        ]
        for text, gender, age, gold, reason in cases:
            self.add(suite="S18-归约-已审核", node="N4", case_ref="REVIEWED", text=text,
                     gender=gender, age=age, alt=reviewed, scenario="SYN-REVIEWED",
                     gold_specialty=gold or "", countable=False,
                     remark=f"同义词签核后期望 {reason}")

    def suite_card_nodes(self, source_rows: List[dict]) -> None:
        """S13–S15：N7 确认 / N8 派医生 / N9 占坑。"""
        n8_scenarios = [
            ("DOC-AVAILABLE", "", "该二级≥1 医生有今日班 → doctor.specialty = 卡片二级科"),
            ("DOC-NONE", "1305", "该二级无医生 → DOCTOR_UNAVAILABLE"),
            ("DOC-FULL", "1305", "该二级满员 → DOCTOR_UNAVAILABLE"),
            ("DOC-L1-ONLY", "1305", "只有一级名医生，不派 → DOCTOR_UNAVAILABLE"),
            ("DOC-TIMEOUT", "1110", "匹配超时 → 转人工，不再打 messages"),
            ("DOC-RETRY", "1305", "重试仍无医生 → 订单不重复插入"),
        ]
        taken = [r for r in source_rows if r["expect_status_code"] == 4 and r["expect_route_certainty"] == "CERTAIN"]
        sample = taken[::max(1, len(taken) // 900)][:900]
        for idx, row in enumerate(sample):
            prior = row["input"]
            # N7 确认不改科
            self._card_row(row, "S13-确认-N7", "N7", f"CONFIRM-AS-IS-{idx}", "CONFIRM",
                           "CONFIRM-AS-IS", "", "确认不改科 → PATCHED/AS_IS 判 AS_IS，科不变")
            # N7 改科到某个已开通二级
            self._card_row(row, "S13-确认-N7", "N7", f"CONFIRM-PATCH-{idx}", "PATCH",
                           "PATCH-SPEC-" + row["expect_specialty"], "",
                           "改科后卡片与订单都用新二级科")
            self._card_row(row, "S13-确认-N7", "N7", "CONFIRM-NOT-READY", "CONFIRM",
                           "CONFIRM-WITHOUT-CARD", "1102", "未出卡就确认 → CARD_NOT_READY")
            self._card_row(row, "S13-确认-N7", "N7", "CONFIRM-TWICE", "CONFIRM",
                           "CONFIRM-AGAIN", "1105", "已确认再确认 → ALREADY_CONFIRMED")
            self._card_row(row, "S13-确认-N7", "N7", "PATCH-HUMAN", "PATCH",
                           "PATCH-DESC-转人工", "1110", "改卡描述含转人工 → NEED_HUMAN，卡片作废")
            for scenario, code, note in n8_scenarios:
                self._card_row(row, "S14-派医生-N8", "N8", scenario.split("-", 1)[0],
                               "DISPATCH", scenario, code, note)
                self._card_row(row, "S15-占坑-N9", "N9", scenario.split("-", 1)[0],
                               "ORDER", scenario, code, note)
            self._card_row(row, "S15-占坑-N9", "N9", "ORDER-BEFORE-CONFIRM", "ORDER",
                           "ORDER-NOT-CONFIRMED", "1115", "未确认就下单 → ORDER_NOT_ALLOWED")
            self._card_row(row, "S15-占坑-N9", "N9", "ORDER-TWICE", "ORDER",
                           "ORDER-REPEAT", "1117", "重复下单不同档位 → ALREADY_ORDERED")
            self._card_row(row, "S15-占坑-N9", "N9", "ORDER-ENDED", "ORDER",
                           "ORDER-AFTER-END", "1123", "问诊已结束 → CONSULT_ENDED")
            self._card_row(row, "S15-占坑-N9", "N9", "ORDER-INQUIRY-CLOSED", "ORDER",
                           "ORDER-CLOSED", "1118", "问诊通道维护 → INQUIRY_CLOSED")

    def _card_row(self, src: dict, suite: str, node: str, case_ref: str, action: str,
                  scenario: str, error_code: str, remark: str) -> None:
        key = ("CARD", src["input"], scenario)
        if key in self.seen_input:
            return
        self.seen_input[key] = suite
        self.seq += 1
        row = {**_blank_row(), **{k: v for k, v in src.items() if k.startswith("expect_")}}
        row.update({
            "case_id": f"TC{self.seq:06d}",
            "suite": suite, "node": node, "case_ref": case_ref, "action": action,
            "turn": 2, "scenario": scenario, "input": src["input"],
            "prior_input": "", "slot_gender": src["slot_gender"], "slot_age": src["slot_age"],
            "slot_duration": src["slot_duration"],
            "gold_department": src["gold_department"], "gold_specialty": src["gold_specialty"],
            "gold_hit": "", "countable": "0",
            "variant_slot": "", "variant_tone": "", "variant_noise": "",
            "expect_error_code": error_code, "remark": remark,
        })
        if action == "CONFIRM" and scenario == "CONFIRM-AS-IS":
            row["expect_status_code"] = 5
            row["expect_status_name"] = "已确认"
            row["expect_route_source"] = "PATIENT"
            row["expect_route_reason"] = "PATIENT_CONFIRM"
        if scenario.startswith("PATCH-SPEC-"):
            target = scenario[len("PATCH-SPEC-"):]
            row["expect_specialty"] = target
            row["expect_route_source"] = "PATIENT"
            row["expect_route_reason"] = "PATIENT_CONFIRM"
            row["expect_route_certainty"] = "CERTAIN"
            row["expect_evidence"] = "患者确认科室"
            row["expect_status_code"] = 4
            row["expect_status_name"] = "卡片待确认"
        if scenario == "PATCH-DESC-转人工":
            row["expect_status_code"] = 6
            row["expect_status_name"] = "需要人工"
            row["expect_safety_status"] = "NEED_HUMAN"
            row["expect_specialty"] = ""
            row["expect_department"] = ""
            row["expect_route_reason"] = ""
        if suite == "S14-派医生-N8" and not error_code:
            row["expect_status_code"] = 5
            row["expect_status_name"] = "已确认"
        if suite == "S15-占坑-N9" and not error_code:
            row["expect_status_code"] = 5
            row["expect_status_name"] = "已确认"
        row["spec_assert"] = _card_assert(action, scenario, error_code, row.get("expect_specialty", ""))
        self.rows.append(row)

    def suite_model_switch(self, source_rows: List[dict]) -> None:
        """S17 模型开关：规则 CERTAIN 不因模型改科；CONFLICT 才允许模型，失败必须全科。"""
        certain_rows = [r for r in source_rows
                        if r["expect_route_certainty"] == "CERTAIN"
                        and r["expect_route_source"] == "RULE"
                        and r["expect_status_code"] == 4]
        for row in certain_rows[::max(1, len(certain_rows) // 700)][:700]:
            key = ("MODEL", row["input"], "CERTAIN")
            if key in self.seen_input:
                continue
            self.seen_input[key] = "S17"
            self.seq += 1
            new_row = {**_blank_row(), **{k: v for k, v in row.items() if k.startswith("expect_")}}
            new_row.update({
                "case_id": f"TC{self.seq:06d}", "suite": "S17-模型开关", "node": "N6",
                "case_ref": "MODEL-MUST-NOT-CHANGE", "action": "MESSAGE", "turn": 1,
                "scenario": "MODEL-ON-CERTAIN", "input": row["input"],
                "session_gender": row.get("session_gender", ""),
                "session_age": row.get("session_age", ""),
                "gold_department": row["expect_department"], "gold_specialty": row["expect_specialty"],
                "gold_hit": "1", "countable": "1",
                "variant_slot": "", "variant_tone": "", "variant_noise": "",
                "remark": "规则 CERTAIN 禁调模型，科目不得被模型改掉",
            })
            self.rows.append(new_row)
        unc_rows = [r for r in source_rows if r["expect_route_certainty"] == "UNCERTAIN"]
        for row in unc_rows[::max(1, len(unc_rows) // 400)][:400]:
            key = ("MODEL", row["input"], "UNCERTAIN")
            if key in self.seen_input:
                continue
            self.seen_input[key] = "S17"
            self.seq += 1
            new_row = {**_blank_row(), **{k: v for k, v in row.items() if k.startswith("expect_")}}
            new_row.update({
                "case_id": f"TC{self.seq:06d}", "suite": "S17-模型开关", "node": "N6",
                "case_ref": "MODEL-ALLOWED-FALLBACK", "action": "MESSAGE", "turn": 1,
                "scenario": "MODEL-OFF", "input": row["input"],
                "session_gender": row.get("session_gender", ""),
                "session_age": row.get("session_age", ""),
                "expect_specialty": "全科", "expect_department": "全科",
                "expect_route_source": "FALLBACK", "expect_route_certainty": "UNCERTAIN",
                "gold_department": "", "gold_specialty": "", "gold_hit": "", "countable": "0",
                "variant_slot": "", "variant_tone": "", "variant_noise": "",
                "remark": "CONFLICT/AMBIGUOUS 才允许模型；模型关必须落全科 FALLBACK",
            })
            self.rows.append(new_row)

    def write(self, path: pathlib.Path) -> None:
        target = path
        with target.open("w", encoding="utf-8-sig", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=COLUMNS, extrasaction="ignore")
            writer.writeheader()
            writer.writerows(self.rows)


TONE_ONSET = {
    "TONE05-补述": "头晕",
    "TONE54-多主诉跨科": "牙疼",
    "TONE55-否定口吻": "胃痛",
}


def _render_slot(pkg: dict, core_term: str, core: dict, chip: Optional[str]) -> str:
    gender = core["gender"]
    male_only = core["dept_code"] == 3
    female_only = core["dept_code"] == 6
    g = pkg["g"]
    if g == "女" and male_only:
        g = "男"
    if g == "男" and female_only:
        g = "女"
    age = pkg["a"]
    if age and int(age) >= 14 and core["dept_code"] == 8:
        age = str(core["age"])
    duration = chip if chip else pkg["d"]
    return pkg["fmt"].format(g=g, a=age, core=core_term, d=duration)


def _slot_supply(pkg: dict, core: dict) -> tuple:
    """绑就诊人的场景下性别年龄由会话给出，其余从原话里抽。"""
    if pkg.get("explicit"):
        return core["gender"], core["age"]
    return None, None


def _blank_row() -> dict:
    return {name: "" for name in COLUMNS}


def main(core_limit: Optional[int] = None, pad: bool = True,
         out: pathlib.Path = OUT_CSV, target_total: int = 88_000) -> None:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:  # pragma: no cover
        pass
    replica = Replica()
    snap = replica.snapshot
    print(f"目录版本 {snap.version}｜一级科 {len(snap.enabled)}｜二级科 {len(snap.enabled_specialty_names())}")

    builder = Builder(replica)
    cores = build_cores(snap)
    if core_limit:
        cores = cores[:core_limit]
    print(f"核心词条 {len(cores)}")

    builder.suite_gold_137()
    builder.suite_core_bulk(cores, target_total=target_total)
    print(f"S1 后累计 {len(builder.rows)}")

    urgent = json.loads(URGENT_JSON.read_text(encoding="utf-8"))
    builder.suite_safety_baseline(urgent["rules"])
    builder.suite_safety_draft(urgent["rules"])
    builder.suite_safety_negative()
    builder.suite_clarity()
    builder.suite_slots()
    builder.suite_canon()
    builder.suite_reviewed_synonyms()
    builder.suite_symptoms()
    builder.suite_boundary()
    builder.suite_negative_boundary()
    builder.suite_negative()
    print(f"专项后累计 {len(builder.rows)}")

    builder.suite_slang(cores)
    builder.suite_card_nodes(list(builder.rows))
    builder.suite_model_switch([r for r in builder.rows if r["suite"] == "S1-正向-二级科词条"])
    print(f"最终 {len(builder.rows)}")

    if pad and len(builder.rows) < PAD_TOTAL:
        print(f"不足 {PAD_TOTAL}，按组合采样补足")
        _pad_to_target(builder, cores)

    if builder.weak_cores:
        print(f"非锚点词条 {len(builder.weak_cores)} 个（基线就不落本科，已排除出金标分母），前 15 个：")
        for item in builder.weak_cores[:15]:
            print(f"  {item[0]}/{item[1]} 词条「{item[2]}」({item[3]}) → 现网 {item[4]} / {item[5]}")

    builder.write(out)
    print(f"写出 {out} 行数 {len(builder.rows)}")


PAD_TOTAL = 150_000


def _pad_to_target(builder: Builder, cores: List[dict]) -> None:
    """用「词条 × 槽位 × 口吻 × 噪声」再采样，把总量补够。"""
    rnd = random.Random(424242)
    extra_slots = list(SLOT_PACKAGES)
    attempts = 0
    max_attempts = 400_000
    while len(builder.rows) < PAD_TOTAL and attempts < max_attempts:
        attempts += 1
        core = rnd.choice(cores)
        pkg_name = rnd.choice(extra_slots)
        pkg = SLOT_PACKAGES[pkg_name]
        tone_title, tone_pattern = rnd.choice(TONE_STRUCTURE)
        noise_title, noise_fn = rnd.choice(NOISES)
        onset = TONE_ONSET.get(tone_title) or rnd.choice(["头晕", "乏力", "恶心", "皮疹", "牙疼", "失眠"])
        piece = noise_fn(tone_pattern.format(core=core["term"], nth=onset))
        chip = rnd.choice(DURATION_CHIPS) if pkg_name.endswith("SLOT18") else None
        text = _render_slot(pkg, piece, core, chip)
        gender, age = _slot_supply(pkg, core)
        builder.add(
            suite="S1-正向-二级科词条", node="N6", case_ref=f"{core['dept']}/{core['spec']}",
            text=text, gender=gender, age=age, countable=False,
            variant_slot=pkg_name, variant_tone=tone_title, variant_noise=noise_title,
            remark="补足采样")
    if len(builder.rows) < 100_000:
        raise RuntimeError(f"组合空间用尽，仅生成 {len(builder.rows)} 条")
    print(f"补足后 {len(builder.rows)}")


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="生成挂号全链路测试数据集")
    parser.add_argument("--cores", type=int, default=None, help="只取前 N 个核心词条（快速试跑用）")
    parser.add_argument("--target", type=int, default=88_000, help="S1 词条集合的行数目标")
    parser.add_argument("--no-pad", action="store_true", help="不补足到 10 万")
    parser.add_argument("--out", type=str, default=str(OUT_CSV), help="输出 CSV 路径")
    args = parser.parse_args()
    main(core_limit=args.cores, pad=not args.no_pad,
         out=pathlib.Path(args.out), target_total=args.target)
