# -*- coding: utf-8 -*-
"""全链路规则复刻器（N1 安全 → N2 含糊 → N3 槽位 → N4 归约 → N5 症状 → N6 挂科）。

标签不手抄，全部由这里的规则算出；唯一事实来源：

- 科室 / 二级科 / 挂词 / 病名 / 错别字 / 不确定标记 / 歧义词 / 归约同义
  → consult-registration-agent-service/src/main/resources/department/catalog-seed.json
- 急症与转人工规则（BASELINE / DRAFT 影子）
  → consult-registration-agent-service/src/main/resources/safety/urgent-catalog.json

复刻口径（Java 侧对应文件）::

    N1  RuleSafetyEngine + SafetyPolicy.view          NBSP (不再{" "}: draft-mode SHADOW → DRAFT 只记影子不拦挂号)
    N2  ComplaintClarity.isVague + ConsultationService.shouldAskForDetail（只追问一次）
    N3  SlotParser + Slots.nextMissing（性别 → 年龄 → 病程）
    N4  ComplaintCanon.rule（规则层，不含模型）
    N5  SymptomExtractor.extract
    N6  DepartmentRouter.route（CERTAIN 不调模型）
    -   TriageEngine.run 的融合：NO_HIT 先走 informal-lexicon.json 词表模型，解不出再打 DeepSeek；
        评测不得关模型，密钥缺失直接失败。

会话状态常量对照 ConsultationStatus：
1 待描述 NEED_DESCRIPTION / 2 待补槽 NEED_SLOT / 3 急症分流 URGENT /
4 卡片待确认 CARD_READY / 5 已确认 CONFIRMED / 6 需要人工 NEED_HUMAN /
7 安全兜底 SAFE_FALLBACK / 8 渠道不适用 UNSUITABLE_FOR_ONLINE
"""
from __future__ import annotations

import json
import pathlib
import re
import unicodedata
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Sequence, Tuple

ROOT = pathlib.Path(__file__).resolve().parents[3]
AGENT_RES = ROOT / "consult-registration-agent" / "consult-registration-agent-service" / "src" / "main" / "resources"
CATALOG_JSON = AGENT_RES / "department" / "catalog-seed.json"
INFORMAL_JSON = AGENT_RES / "department" / "informal-lexicon.json"
URGENT_JSON = AGENT_RES / "safety" / "urgent-catalog.json"

# ---------------------------------------------------------------- N3 槽位
AGE_PATTERN = re.compile(r"(\d{1,3})岁")
DURATION_PATTERN = re.compile(
    r"好几天|半个多月|一个多月|两个多月|两三天|三四天|三五天"
    r"|(\d+|[一二三四五六七八九十两半]+)(个多)?(小时|天|周|个月|月|年)"
    r"|\d+\s*days?"
)
TRAD_TO_SIMP = str.maketrans({
    "頭": "头", "瀉": "泻", "膚": "肤", "過": "过", "內": "内", "壓": "压", "結": "结",
    "經": "经", "調": "调", "陰": "阴", "慮": "虑", "鬱": "郁", "視": "视", "鳴": "鸣",
    "腫": "肿", "癢": "痒", "濕": "湿", "瘡": "疮", "貧": "贫", "頸": "颈", "關": "关",
    "節": "节", "質": "质", "鬆": "松", "燒": "烧", "嘔": "呕", "惡": "恶", "暈": "晕",
    "眩": "眩", "蕁": "荨", "腎": "肾", "傷": "伤", "燙": "烫", "齒": "齿", "齦": "龈",
    "潰": "溃", "瘍": "疡", "啞": "哑", "聾": "聋", "癇": "痫",
})
VAGUE_PATTERN = re.compile(r"(不舒服|难受|不适|不太好|不好受|有点事|不太对|生病了|身体不适)")

# ---------------------------------------------------------------- N1 安全
SPLITTERS = ["但是", "不过", "可是", "然而", "，", "。", "；", "！", "？", ",", ".", ";", "!", "?", "\n"]
DOUBLE_NEGATIONS = ["不是没有", "没有不", "并非没有"]
UNCERTAIN_WORDS = ["是不是", "会不会", "好像", "可能", "不确定", "说不清"]
NEGATIONS = ["没有", "并非", "不是", "否认", "没", "无", "不", "未"]
TRAILING_NEGATIONS = ["不是", "并非"]
RESOLVED_WORDS = ["已经好了", "缓解了", "不疼了", "消失了", "现在没事"]
CURRENT_ANAPHORA = ["现在开始痛了", "现在开始疼了", "又痛了", "又疼了"]
WINDOW_SIZE = 6

# ---------------------------------------------------------------- N4 归约
CANON_MAX_TERMS = 2
CANON_MIN_MODEL_LENGTH = 6
HISTORY_BEFORE = re.compile(r"(既往|以前|曾经|之前|老毛病|一直有)[^，。；,;.!?]{0,6}$")
HISTORY_AFTER = re.compile(r"^[^，。；,;.!?]{0,3}(病史|多年|好几年|十几年|吃药控制)")

SAFETY_PHRASES = ["呼吸困难", "胸痛"]

STATUS_NAME = {
    1: "待描述",
    2: "待补槽",
    3: "急症分流",
    4: "卡片待确认",
    5: "已确认",
    6: "需要人工",
    7: "安全兜底",
    8: "渠道不适用",
}
GENDER_NAME = {1: "男", 2: "女"}


# ---------------------------------------------------------------- 归一
def compact_text(original: str) -> str:
    """Java CompactText.of：NFKC → 去空白 → 转小写。"""
    nfkc = unicodedata.normalize("NFKC", original or "")
    return "".join(ch for ch in nfkc if not ch.isspace()).lower()


_INFORMAL_ENTRIES: Optional[List[Tuple[str, str]]] = None


def load_informal_entries() -> List[Tuple[str, str]]:
    global _INFORMAL_ENTRIES
    if _INFORMAL_ENTRIES is None:
        payload = json.loads(INFORMAL_JSON.read_text(encoding="utf-8"))
        items = [
            (str(item.get("from") or ""), str(item.get("to") or ""))
            for item in payload.get("entries") or []
            if item.get("from") and item.get("to")
        ]
        items.sort(key=lambda pair: (-len(pair[0]), pair[0]))
        _INFORMAL_ENTRIES = items
    return _INFORMAL_ENTRIES


def _compact_view(original: str) -> Tuple[str, List[int], List[int]]:
    src = original or ""
    compact: List[str] = []
    starts: List[int] = []
    ends: List[int] = []
    index = 0
    while index < len(src):
        nxt = index + 1
        ch = src[index]
        if not ch.isspace():
            piece = unicodedata.normalize("NFKC", ch)
            for mapped in piece:
                compact.append(mapped.lower())
                starts.append(index)
                ends.append(nxt)
        index = nxt
    return "".join(compact), starts, ends


def _ascii_word(value: str) -> bool:
    normalized = unicodedata.normalize("NFKC", value or "")
    return bool(normalized) and all(ord(ch) <= 127 and ch.isalnum() for ch in normalized)


def _ascii_boundaries(original: str, start: int, end: int) -> bool:
    before = original[start - 1] if start > 0 else ""
    after = original[end] if end < len(original) else ""
    return not (_ascii_word(before) or _ascii_word(after))


def _unsafe_informal_context(original: str, start: int, end: int) -> bool:
    before = original[max(0, start - 16):start]
    after = original[end:min(len(original), end + 12)]
    negation_before = before
    boundary = 0
    for marker in ("但是", "但有", "不过", "可是", "然而", "现在有", "目前有", "后来有", "但"):
        at = before.rfind(marker)
        if at >= 0:
            boundary = max(boundary, at + len(marker))
    negation_before = before[boundary:]
    return bool(
        re.search(r"(没有|并非|不是|否认|不算|没|无|未)[^，。；,;.!?]{0,6}$", negation_before)
        or re.search(r"(既往|以前|曾经|之前|老毛病|一直有)[^，。；,;.!?]{0,8}$", before)
        or re.search(r"^[^，。；,;.!?]{0,3}(病史|多年|好几年|十几年|吃药控制)", after)
        or re.search(r"(我妈|我爸|妈妈|爸爸|母亲|父亲|家人|家族)[^，。；,;.!?]{0,4}$", before)
    )


def _rewrite_informal_details(original: str) -> Tuple[str, List[Tuple[int, int, str, str]]]:
    entries = load_informal_entries()
    if not original or not entries:
        return "", []
    compact, starts, ends = _compact_view(original)
    if not compact:
        return "", []
    hits: List[Tuple[int, int, str, str]] = []
    index = 0
    while index < len(compact):
        best: Optional[Tuple[str, str]] = None
        for src, dst in entries:
            if not src or index + len(src) > len(compact):
                continue
            end = index + len(src)
            if not compact.startswith(src, index):
                continue
            if _ascii_word(src) and not _ascii_boundaries(original, starts[index], ends[end - 1]):
                continue
            if best is None or len(src) > len(best[0]):
                best = (src, dst)
        if best is None:
            index += 1
            continue
        end = index + len(best[0])
        orig_start, orig_end = starts[index], ends[end - 1]
        if not _unsafe_informal_context(original, orig_start, orig_end):
            hits.append((orig_start, orig_end, original[orig_start:orig_end], best[1]))
        index = end
    if not hits:
        return "", []
    rewritten = original
    for orig_start, orig_end, _surface, dst in reversed(hits):
        rewritten = rewritten[:orig_start] + dst + rewritten[orig_end:]
    return rewritten, hits


def rewrite_informal(original: str) -> Tuple[str, str]:
    """口语词表替换。没有安全命中时 rewritten 为空串。"""
    rewritten, hits = _rewrite_informal_details(original)
    return rewritten, hits[0][2] if hits else ""


def lexicon_interpret(description: str, snap: Snapshot, age: Optional[int], gender: Optional[int],
                      candidates: Sequence[str]) -> Optional[RouteDecision]:
    rewritten, hits = _rewrite_informal_details(description or "")
    if not rewritten or not hits:
        return None
    decoded = canon_rule(rewritten, snap)
    routed = route(rewritten, decoded, snap, age=age, gender=gender)
    if not routed.certain() or routed.reason in ("NO_HIT_SHORT", "UNCERTAIN_MARKER"):
        return None
    specialty = routed.advice.specialty
    if not specialty or (candidates and specialty not in candidates):
        return None
    routed_evidence = routed.advice.evidence or ""
    evidence = next(
        (surface for _start, _end, surface, target in hits
         if routed_evidence and (target in routed_evidence or routed_evidence in target)),
        "",
    )
    if not evidence:
        return None
    reason = "MODEL_TERM_ROUTED" if decoded.terms else "MODEL_PICK"
    advice = Advice(
        routed.advice.department,
        routed.advice.department_code,
        routed.advice.specialty,
        evidence,
    )
    return RouteDecision(
        advice, "UNCERTAIN", "MODEL", reason, list(candidates),
        model_name="informal-lexicon", prompt_version="lexicon-v1")


def llm_interpret(description: str, snap: Snapshot, age: Optional[int], gender: Optional[int],
                  candidates: Sequence[str]) -> Optional[RouteDecision]:
    from complaint_llm import PROMPT_VERSION, interpret, live_enabled
    if not live_enabled():
        return None
    allow = list(snap.disease_index)
    got = interpret(
        description, age, gender, allow, list(candidates or []),
        catalog_version=snap.version)
    if not got:
        return None
    model_name = got.get("model_name") or ""
    prompt_version = got.get("prompt_version") or PROMPT_VERSION
    terms = got.get("terms") or []
    if terms:
        canon_model = CanonResult(
            terms=[(item.get("term") or "", item.get("evidence") or "") for item in terms],
            certainty="CERTAIN", source="MODEL", reason="MODEL_TERMS")
        routed = route(description, canon_model, snap, age=age, gender=gender)
        if routed.certain() and (not candidates or routed.advice.specialty in candidates):
            return RouteDecision(
                routed.advice, "UNCERTAIN", "MODEL", "MODEL_TERM_ROUTED", list(candidates),
                model_name=model_name, prompt_version=prompt_version)
    ref = snap.ref_of_specialty(got["specialty"])
    if ref is None:
        return None
    if candidates and got["specialty"] not in candidates:
        return None
    return RouteDecision(
        _advice_of(snap, ref, got.get("evidence") or got["specialty"]),
        "UNCERTAIN", "MODEL", "MODEL_PICK", list(candidates),
        model_name=model_name, prompt_version=prompt_version)


# ---------------------------------------------------------------- 数据载体
@dataclass(frozen=True)
class Specialty:
    name: str
    treat_desc: str
    keywords: Tuple[str, ...]
    diseases: Tuple[str, ...]

    def match_terms(self) -> List[str]:
        if self.keywords:
            return list(self.keywords)
        terms = [self.name] if self.name else []
        terms += [part for part in (self.treat_desc or "").split() if len(part) >= 2]
        return terms


@dataclass
class DeptRecord:
    code: int
    name: str
    enabled: bool
    sort: int
    fallback: bool
    gender_scope: int
    child_age_lt: Optional[int]
    treat_desc: str
    specialties: List[Specialty]
    routing_keywords: Tuple[str, ...] = ()
    override_keywords: Tuple[str, ...] = ()

    def male_only(self) -> bool:
        return self.gender_scope == 1

    def female_only(self) -> bool:
        return self.gender_scope == 2

    def pediatric_catch(self) -> bool:
        return self.child_age_lt is not None

    def default_specialty(self) -> str:
        for spec in self.specialties:
            if self.name == spec.name:
                return spec.name
        return self.specialties[0].name if self.specialties else self.name

    def has_specialty(self, specialty: Optional[str]) -> bool:
        if not specialty:
            return False
        for spec in self.specialties:
            if specialty == spec.name:
                return True
        return not self.specialties and self.name == specialty


@dataclass(frozen=True)
class SpecialtyRef:
    department_code: int
    specialty: str


@dataclass
class Hit:
    department_code: int
    specialty: str
    at: int
    keyword: str


@dataclass
class Advice:
    department: Optional[str]
    department_code: Optional[int]
    specialty: str
    evidence: Optional[str]


@dataclass
class RouteDecision:
    advice: Advice
    certainty: str
    source: str
    reason: str
    candidates: List[str] = field(default_factory=list)
    model_name: str = ""
    prompt_version: str = ""

    def certain(self) -> bool:
        return self.certainty == "CERTAIN"


@dataclass
class CanonResult:
    terms: List[Tuple[str, str]] = field(default_factory=list)      # (term, evidence)
    history_terms: List[str] = field(default_factory=list)
    symptoms: List[Tuple[str, bool]] = field(default_factory=list)  # (phrase, ambiguous)
    shadows: List[str] = field(default_factory=list)
    certainty: str = "CERTAIN"
    source: str = "RULE"
    reason: str = ""

    def certain(self) -> bool:
        return self.certainty == "CERTAIN"


@dataclass
class SafetyView:
    stop: bool
    status_code: Optional[int]
    matched_rules: List[str]
    shadow_rules: List[str]
    stop_code: Optional[int] = None


# ---------------------------------------------------------------- 目录快照
class Snapshot:
    """一次加载的匹配快照，口径对齐 DepartmentSnapshot。"""

    def __init__(self, records: List[DeptRecord], version: str,
                 typos: List[Tuple[str, str]], uncertain: List[str],
                 ambiguous: List[str], synonyms: List[dict]):
        self.version = version
        self.all_records = list(records)
        self.enabled = [r for r in records if r.enabled]
        self.by_code = {r.code: r for r in self.enabled}
        fallback = [r for r in self.enabled if r.fallback]
        if not self.enabled:
            raise ValueError("科室目录没有已开通科室")
        if len(fallback) != 1 or fallback[0].name != "全科":
            raise ValueError("科室目录必须且只能有一个兜底科「全科」")
        self.fallback = fallback[0]
        self.typos = sorted(typos, key=lambda item: -len(item[0]))
        self.uncertain_markers = sorted(uncertain, key=len, reverse=True)
        self.ambiguous = list(ambiguous)
        self.synonyms = synonyms
        self.symptom_phrases = self._build_symptom_phrases()
        self.disease_index = self._build_disease_index()
        self.phrase_refs = self._build_phrase_refs()

    # ---------------- 索引构建
    def _build_symptom_phrases(self) -> List[str]:
        seen: Dict[str, None] = {}
        for dept in self.enabled:
            for kw in dept.routing_keywords:
                seen.setdefault(kw, None)
            for spec in dept.specialties:
                for kw in spec.match_terms():
                    seen.setdefault(kw, None)
        for phrase in SAFETY_PHRASES:
            seen.setdefault(phrase, None)
        return list(seen)

    def _build_disease_index(self) -> Dict[str, SpecialtyRef]:
        index: Dict[str, SpecialtyRef] = {}
        for dept in self.enabled:
            for spec in dept.specialties:
                for term in spec.diseases:
                    if term:
                        index.setdefault(term, SpecialtyRef(dept.code, spec.name))
        return index

    def _build_phrase_refs(self) -> Dict[str, List[SpecialtyRef]]:
        refs: Dict[str, List[SpecialtyRef]] = {}
        for dept in self.enabled:
            used = set()
            for spec in dept.specialties:
                for term in spec.match_terms():
                    used.add(term)
                    self._add_ref(refs, term, SpecialtyRef(dept.code, spec.name))
            default_ref = SpecialtyRef(dept.code, dept.default_specialty())
            for term in dept.routing_keywords:
                if term not in used:
                    self._add_ref(refs, term, default_ref)
            if dept.name not in used and len(dept.name) >= 2:
                self._add_ref(refs, dept.name, default_ref)
        return refs

    @staticmethod
    def _add_ref(refs: Dict[str, List[SpecialtyRef]], term: str, ref: SpecialtyRef) -> None:
        if not term:
            return
        bucket = refs.setdefault(term, [])
        if ref not in bucket:
            bucket.append(ref)

    # ---------------- 查询
    def is_enabled(self, code: int) -> bool:
        return code in self.by_code

    def record_of(self, code: int) -> Optional[DeptRecord]:
        return self.by_code.get(code)

    def ref_of_specialty(self, specialty: str) -> Optional[SpecialtyRef]:
        for dept in self.enabled:
            if dept.has_specialty(specialty):
                return SpecialtyRef(dept.code, specialty)
        return None

    def enabled_specialty_names(self) -> List[str]:
        names: Dict[str, None] = {}
        for dept in self.enabled:
            for spec in dept.specialties:
                names.setdefault(spec.name, None)
        return list(names)

    def is_disease(self, term: str) -> bool:
        return term in self.disease_index

    def is_ambiguous(self, phrase: str) -> bool:
        return phrase in self.ambiguous

    def for_match(self, text: str) -> str:
        out = compact_text(text).translate(TRAD_TO_SIMP)
        for wrong, right in self.typos:
            out = out.replace(wrong, right)
        return out

    def uncertain_department(self, original: str) -> bool:
        text = compact_text(original)
        return any(marker in text for marker in self.uncertain_markers)

    def without_enabled(self, code: int) -> "Snapshot":
        """对标 DepartmentSnapshot.withoutEnabled：该一级科移出启用集合。"""
        records = [r for r in self.all_records if r.code != code]
        return Snapshot(records, self.version, self.typos, list(self.uncertain_markers),
                        list(self.ambiguous), self.synonyms)

    def with_synonyms_reviewed(self, rule_ids: Sequence[str]) -> "Snapshot":
        """把指定归约规则标成已审核（REVIEWED），用于 N4 审核后口径。"""
        replaced = []
        for rule in self.synonyms:
            item = dict(rule)
            if item.get("ruleId") in set(rule_ids):
                item["status"] = "REVIEWED"
            replaced.append(item)
        return Snapshot(self.all_records, self.version, self.typos, list(self.uncertain_markers),
                        list(self.ambiguous), replaced)


# ---------------------------------------------------------------- N5 症状
def extract_symptoms(description: str, snap: Snapshot) -> List[str]:
    text = snap.for_match(description)
    found: List[str] = []
    index = 0
    while index < len(text):
        match = _longest_at(text, index, snap.symptom_phrases)
        if match is None:
            index += 1
            continue
        if match not in found:
            found.append(match)
        index += len(match)
    return found


def _longest_at(text: str, index: int, phrases: Sequence[str]) -> Optional[str]:
    best = None
    for phrase in phrases:
        if not phrase or not text.startswith(phrase, index):
            continue
        if best is None or len(phrase) > len(best):
            best = phrase
    return best


# ---------------------------------------------------------------- N4 归约
def canon_rule(description: str, snap: Snapshot) -> CanonResult:
    text = snap.for_match(description)
    if not text:
        return CanonResult(reason="EMPTY")
    if snap.uncertain_department(description):
        return CanonResult(reason="UNCERTAIN_MARKER")

    current: List[Tuple[str, int, int, str]] = []   # (term, start, end, ruleId)
    history: List[str] = []
    symptoms: List[Tuple[str, bool]] = []
    seen_symptom = set()
    phrases: Dict[str, None] = {}
    for term in snap.disease_index:
        phrases.setdefault(term, None)
    for phrase in snap.symptom_phrases:
        phrases.setdefault(phrase, None)
    for phrase in snap.ambiguous:
        phrases.setdefault(phrase, None)
    phrase_list = list(phrases)

    index = 0
    while index < len(text):
        match = _longest_at(text, index, phrase_list)
        if match is None:
            index += 1
            continue
        span = (index, index + len(match))
        if snap.is_disease(match):
            before, after = text[:span[0]], text[span[1]:]
            if bool(HISTORY_BEFORE.search(before)) or bool(HISTORY_AFTER.search(after)):
                if match not in history:
                    history.append(match)
            else:
                if not any(item[0] == match for item in current):
                    current.append((match, span[0], span[1], CanonTermLiteral))
        elif match not in seen_symptom:
            seen_symptom.add(match)
            symptoms.append((match, snap.is_ambiguous(match)))
        index = span[1]

    shadows: List[str] = []
    literal = bool(current)
    synonym_hit = False
    for rule in snap.synonyms:
        hit = _synonym_match(rule, text)
        if hit is None:
            continue
        start, end = hit
        if rule.get("status") == "REVIEWED":
            term = rule.get("target", "")
            span = (start, end)
            before, after = text[:span[0]], text[span[1]:]
            if bool(HISTORY_BEFORE.search(before)) or bool(HISTORY_AFTER.search(after)):
                if term not in history:
                    history.append(term)
            elif not any(item[0] == term for item in current):
                current.append((term, span[0], span[1], rule.get("ruleId", "")))
                synonym_hit = True
        else:
            shadows.append(rule.get("ruleId", ""))

    current.sort(key=lambda item: item[1])
    terms = current[:CANON_MAX_TERMS]
    if terms:
        return CanonResult(
            terms=[(item[0], text[item[1]:item[2]]) for item in terms],
            history_terms=history, symptoms=symptoms, shadows=shadows,
            certainty="CERTAIN", reason="DISEASE_LITERAL" if literal else "SYNONYM")

    clear = any((not amb) for _, amb in symptoms)
    ambiguous = any(amb for _, amb in symptoms)
    if clear:
        return CanonResult(symptoms=symptoms, shadows=shadows, certainty="CERTAIN", reason="SYMPTOM_ONLY")
    if ambiguous:
        return CanonResult(symptoms=symptoms, shadows=shadows, certainty="UNCERTAIN", reason="AMBIGUOUS_ONLY")
    if (len(text) >= CANON_MIN_MODEL_LENGTH
            or any(("a" <= ch <= "z") or ("A" <= ch <= "Z") for ch in text)
            or bool(rewrite_informal(description)[0])):
        return CanonResult(symptoms=symptoms, shadows=shadows, certainty="UNCERTAIN", reason="NO_HIT")
    return CanonResult(symptoms=symptoms, shadows=shadows, certainty="CERTAIN", reason="TOO_SHORT")


CanonTermLiteral = "LITERAL"


def _synonym_match(rule: dict, compact: str):
    groups = rule.get("all") or []
    if not compact or not groups:
        return None
    for none in rule.get("none") or []:
        if none and none in compact:
            return None
    start = None
    end = -1
    for group in groups:
        at = -1
        word = None
        for item in group:
            if not item:
                continue
            found = compact.find(item)
            if found >= 0 and (at < 0 or found < at):
                at = found
                word = item
        if at < 0:
            return None
        if start is None or at < start:
            start = at
            end = at + len(word)
    pattern = rule.get("pattern")
    if pattern:
        matcher = re.search(pattern, compact)
        if not matcher:
            return None
        start, end = matcher.start(), matcher.end()
    return (start, end)


# ---------------------------------------------------------------- N3 槽位
def parse_gender(text: str) -> Optional[int]:
    male_at = text.find("男")
    female_at = text.find("女")
    if male_at < 0 and female_at < 0:
        return None
    if male_at < 0:
        return 2
    if female_at < 0:
        return 1
    return 1 if male_at < female_at else 2


def parse_age(text: str) -> Optional[int]:
    matcher = AGE_PATTERN.search(text)
    if not matcher:
        return None
    value = int(matcher.group(1))
    if value < 1 or value > 120:
        return None
    return value


def parse_duration(text: str) -> Optional[str]:
    matcher = DURATION_PATTERN.search(text)
    return matcher.group() if matcher else None


def next_missing(gender: Optional[int], age: Optional[int], duration: Optional[str]) -> Optional[str]:
    if gender is None:
        return "GENDER"
    if age is None:
        return "AGE"
    if duration is None:
        return "DURATION"
    return None


# ---------------------------------------------------------------- N2 含糊
def is_vague(sentence: str, snap: Snapshot) -> bool:
    if sentence is None:
        return True
    text = sentence.strip()
    if not text:
        return True
    if extract_symptoms(text, snap):
        return False
    canon = canon_rule(text, snap)
    if canon.terms:
        return False
    if parse_duration(text):
        return False
    if parse_gender(text) is not None or parse_age(text) is not None:
        return False
    if VAGUE_PATTERN.search(text):
        return len(text) <= 16
    return len(text) < 6


# ---------------------------------------------------------------- N6 挂科
def _skip_by_gender(dept: DeptRecord, gender: Optional[int]) -> bool:
    if gender == 2 and dept.male_only():
        return True
    if gender == 1 and dept.female_only():
        return True
    return False


def _consider(best: Optional[Hit], haystack: str, dept: DeptRecord,
              specialty: str, keyword: str) -> Optional[Hit]:
    at = haystack.find(keyword)
    if at < 0:
        return None
    if best is None or at < best.at or (at == best.at and len(keyword) > len(best.keyword)):
        return Hit(dept.code, specialty, at, keyword)
    return None


def _consider_longer(best: Optional[Hit], haystack: str, dept: DeptRecord,
                     specialty: str, keyword: str) -> Optional[Hit]:
    at = haystack.find(keyword)
    if at < 0:
        return None
    if best is None or len(keyword) > len(best.keyword) or (
            len(keyword) == len(best.keyword) and at < best.at):
        return Hit(dept.code, specialty, at, keyword)
    return None


def _pediatric_named(specialty: Optional[str]) -> bool:
    return bool(specialty) and (specialty.startswith("小儿") or specialty.startswith("新生儿"))


def _pediatric_term(keyword: Optional[str]) -> bool:
    return bool(keyword) and (keyword.startswith("小儿") or keyword.startswith("儿童")
                              or keyword.startswith("新生儿"))


def _is_child(snap: Snapshot, haystack: str, age: Optional[int]) -> bool:
    pediatric = _pediatric_dept(snap)
    if pediatric is None:
        return False
    if _first_override(haystack, snap, True) is not None:
        return True
    return age is not None and age < pediatric.child_age_lt


def _pediatric_dept(snap: Snapshot) -> Optional[DeptRecord]:
    for dept in snap.enabled:
        if dept.pediatric_catch():
            return dept
    return None


def _first_pediatric_named(haystack: str, snap: Snapshot, gender: Optional[int]) -> Optional[Hit]:
    best: Optional[Hit] = None
    for dept in snap.enabled:
        if _skip_by_gender(dept, gender):
            continue
        for spec in dept.specialties:
            named_spec = _pediatric_named(spec.name)
            for keyword in spec.match_terms():
                if not named_spec and not _pediatric_term(keyword):
                    continue
                hit = _consider_longer(best, haystack, dept, spec.name, keyword)
                if hit is not None:
                    best = hit
    return best


def _first_routing(haystack: str, snap: Snapshot, only: Optional[DeptRecord],
                   gender: Optional[int], child: bool = True) -> Optional[Hit]:
    best: Optional[Hit] = None
    for dept in snap.enabled:
        if only is not None and dept.code != only.code:
            continue
        if only is None and _skip_by_gender(dept, gender):
            continue
        used = set()
        for spec in dept.specialties:
            skip_named = (not child) and only is None and _pediatric_named(spec.name)
            for keyword in spec.match_terms():
                used.add(keyword)
                if skip_named:
                    continue
                hit = _consider(best, haystack, dept, spec.name, keyword)
                if hit is not None:
                    best = hit
        default_spec = dept.default_specialty()
        for keyword in dept.routing_keywords:
            if keyword in used:
                continue
            if (not child) and only is None and dept.pediatric_catch():
                continue
            hit = _consider(best, haystack, dept, default_spec, keyword)
            if hit is not None:
                best = hit
        if dept.name not in used and len(dept.name) >= 2:
            hit = _consider(best, haystack, dept, default_spec, dept.name)
            if hit is not None:
                best = hit
    return best


def _first_override(haystack: str, snap: Snapshot, pediatric_only: bool) -> Optional[Hit]:
    best: Optional[Hit] = None
    for dept in snap.enabled:
        if dept.pediatric_catch() != pediatric_only:
            continue
        for keyword in dept.override_keywords:
            at = haystack.find(keyword)
            if at < 0:
                continue
            if best is None or at < best.at or (at == best.at and len(keyword) > len(best.keyword)):
                best = Hit(dept.code, dept.default_specialty(), at, keyword)
    return best


def _map_to_pediatric(pediatric: DeptRecord, adult_specialty: str) -> str:
    if pediatric.has_specialty(adult_specialty):
        return adult_specialty
    if not adult_specialty:
        return pediatric.default_specialty()
    best = pediatric.default_specialty()
    best_len = 0
    for spec in pediatric.specialties:
        stem = spec.name.replace("小儿", "")
        if stem.endswith("科") and len(stem) > 2:
            stem = stem[:-1]
        if len(stem) < 2:
            continue
        if stem in adult_specialty and len(stem) > best_len:
            best = spec.name
            best_len = len(stem)
    return best


def _pediatric_hit(haystack: str, snap: Snapshot, age: Optional[int], gender: Optional[int],
                   adult_hint: Optional[str]) -> Optional[Hit]:
    pediatric = _pediatric_dept(snap)
    if pediatric is None or not _is_child(snap, haystack, age):
        return None
    named = _first_pediatric_named(haystack, snap, gender)
    if named is not None:
        return named
    routing = _first_routing(haystack, snap, pediatric, gender, True)
    if routing is not None:
        return routing
    marker = _first_override(haystack, snap, True)
    if adult_hint is not None:
        mapped = _map_to_pediatric(pediatric, adult_hint)
        if pediatric.has_specialty(mapped) and (
                mapped != pediatric.default_specialty() or pediatric.has_specialty(adult_hint)):
            return Hit(pediatric.code, mapped, 0, adult_hint)
    adult = _first_routing(haystack, snap, None, gender, True)
    if adult is not None:
        mapped = _map_to_pediatric(pediatric, adult.specialty)
        rec = snap.record_of(adult.department_code)
        if rec is not None and pediatric.has_specialty(mapped) and mapped != pediatric.default_specialty():
            return Hit(pediatric.code, mapped, adult.at, adult.keyword)
        if pediatric.has_specialty(adult.specialty):
            return Hit(pediatric.code, adult.specialty, adult.at, adult.keyword)
        return adult
    if marker is not None:
        return marker
    return Hit(pediatric.code, pediatric.default_specialty(), 0, "儿童")


def _fallback_advice(snap: Snapshot) -> Advice:
    return Advice(snap.fallback.name, snap.fallback.code, snap.fallback.default_specialty(), None)


def _clamp(snap: Snapshot, hit: Hit) -> Advice:
    if not snap.is_enabled(hit.department_code):
        return _fallback_advice(snap)
    rec = snap.record_of(hit.department_code)
    if rec is None:
        return _fallback_advice(snap)
    specialty = hit.specialty if rec.has_specialty(hit.specialty) else rec.default_specialty()
    return Advice(_name_of(hit.department_code), hit.department_code, specialty, hit.keyword)


_DEPARTMENT_NAMES: Dict[int, str] = {}


def route(description: str, canon: CanonResult, snap: Snapshot,
          age: Optional[int] = None, gender: Optional[int] = None) -> RouteDecision:
    """对标 DepartmentRouter.route。"""
    if description is None or not description.strip() or snap.uncertain_department(description):
        return RouteDecision(_fallback_advice(snap), "CERTAIN", "RULE", "UNCERTAIN_MARKER")
    haystack = snap.for_match(description)
    gender = gender if gender is not None else parse_gender(haystack)
    age = age if age is not None else parse_age(haystack)
    child = _is_child(snap, haystack, age)

    terms: List[Tuple[str, str]] = []
    term_refs: List[SpecialtyRef] = []
    for term, evidence in canon.terms:
        ref = snap.disease_index.get(term)
        if ref is None:
            continue
        rec = snap.record_of(ref.department_code)
        if rec is not None and _skip_by_gender(rec, gender):
            continue
        if (not child) and _pediatric_named(ref.specialty):
            continue
        terms.append((term, evidence))
        term_refs.append(ref)

    override = _first_override(haystack, snap, False)
    if override is not None:
        rec = snap.record_of(override.department_code)
        inner: Optional[Hit] = None
        for idx, ref in enumerate(term_refs):
            if ref.department_code == rec.code:
                inner = Hit(override.department_code, ref.specialty, 0, terms[idx][1] or ref.specialty)
                break
        if inner is None:
            inner = _first_routing(haystack, snap, rec, None, True)
        return RouteDecision(_clamp(snap, inner or override), "CERTAIN", "RULE", "OVERRIDE")

    adult_hint = term_refs[0].specialty if term_refs else None
    pediatric = _pediatric_hit(haystack, snap, age, gender, adult_hint)
    if pediatric is not None:
        return RouteDecision(_clamp(snap, pediatric), "CERTAIN", "RULE", "PEDIATRIC")

    clear: List[Tuple[str, str]] = []
    clear_refs: List[SpecialtyRef] = []
    for phrase, ambiguous in canon.symptoms:
        if ambiguous:
            continue
        for ref in snap.phrase_refs.get(phrase, []):
            rec = snap.record_of(ref.department_code)
            if rec is not None and _skip_by_gender(rec, gender):
                continue
            if (not child) and _pediatric_named(ref.specialty):
                continue
            clear.append((phrase, phrase))
            clear_refs.append(ref)
            break

    if term_refs:
        primary = term_refs[0]
        specs: Dict[str, None] = {ref.specialty: None for ref in term_refs}
        only_one = len(specs) == 1
        for ref in clear_refs:
            if ref.department_code != primary.department_code:
                specs[ref.specialty] = None
                only_one = False
        if only_one:
            return RouteDecision(_advice_of(snap, primary, terms[0][1] or primary.specialty),
                                 "CERTAIN", "RULE", "CANON_TERM")
        return _undecided(snap, "CONFLICT", list(specs))

    if not clear:
        if not canon.certain():
            reason = "AMBIGUOUS_ONLY" if canon.reason == "AMBIGUOUS_ONLY" else "NO_HIT"
            return _undecided(snap, reason, snap.enabled_specialty_names())
        named = _first_routing(haystack, snap, None, gender, child)
        if named is not None:
            return RouteDecision(_clamp(snap, named), "CERTAIN", "RULE", "SINGLE_SPECIALTY")
        return RouteDecision(_fallback_advice(snap), "CERTAIN", "RULE", "NO_HIT_SHORT")

    specs = {ref.specialty: None for ref in clear_refs}
    level_one = {ref.department_code: None for ref in clear_refs}
    if len(specs) == 1:
        return RouteDecision(_advice_of(snap, clear_refs[0], clear[0][1]), "CERTAIN", "RULE", "SINGLE_SPECIALTY")
    if len(level_one) == 1:
        return RouteDecision(_advice_of(snap, clear_refs[0], clear[0][1]), "CERTAIN", "RULE", "SAME_L1_EARLIEST")
    return _undecided(snap, "CONFLICT", list(specs))


def canon_reason_of(canon: CanonResult) -> str:
    return canon.reason


def _advice_of(snap: Snapshot, ref: SpecialtyRef, evidence: Optional[str]) -> Advice:
    if ref is None or not snap.is_enabled(ref.department_code):
        return _fallback_advice(snap)
    rec = snap.record_of(ref.department_code)
    specialty = ref.specialty if rec.has_specialty(ref.specialty) else rec.default_specialty()
    return Advice(_name_of(ref.department_code), ref.department_code, specialty, evidence)


def _name_of(code: int) -> str:
    return _DEPARTMENT_NAMES.get(code, str(code))


def _undecided(snap: Snapshot, reason: str, specialties: List[str]) -> RouteDecision:
    candidates = list(specialties)
    general = snap.fallback.default_specialty()
    if general not in candidates:
        candidates.append(general)
    return RouteDecision(_fallback_advice(snap), "UNCERTAIN", "RULE", reason, candidates)


# ---------------------------------------------------------------- N1 安全（规则层）
@dataclass
class SafetyRuleDef:
    rule_id: str
    status: str
    disposition: str
    negated_policy: str
    uncertainty_policy: str
    resolved_policy: str
    missing_context_policy: str
    aliases: List[str]


class RuleSafety:
    """RuleSafetyEngine 的规则层 + SafetyPolicy.view 的裁决顺序。

    命中位点：每条规则独立的占用位图，别名长度降序（避免「胸痛」抢走「持续性胸痛」）。
    处置优先级：unclear（整句） > negated（别名前 6 字） > resolved（整句） > ESCALATE。
    DRAFT 在 SHADOW 下只进 shadow_rules，不改患者状态；ENFORCE 时与 BASELINE 一起参与决策。
    """

    def __init__(self, rules: List[SafetyRuleDef], enforce_draft: bool = False):
        self.rules = rules
        self.enforce_draft = enforce_draft

    # ---------------- 单句扫描
    @staticmethod
    def alias_matches(rule: SafetyRuleDef, sentence: str) -> List[Tuple[int, int]]:
        taken = [False] * len(sentence)
        matches: List[Tuple[int, int]] = []
        for alias in sorted([a for a in rule.aliases if a], key=len, reverse=True):
            needle = compact_text(alias)
            if not needle:
                continue
            start = 0
            while start <= len(sentence) - len(needle):
                at = sentence.find(needle, start)
                if at < 0:
                    break
                end = at + len(needle)
                if not any(taken[pos] for pos in range(at, end)):
                    for pos in range(at, end):
                        taken[pos] = True
                    matches.append((at, end))
                start = at + 1
        matches.sort()
        return matches

    @staticmethod
    def action_of(rule: SafetyRuleDef, sentence: str, start: int, end: int = None) -> str:
        window = sentence[max(0, start - WINDOW_SIZE):start]
        after_at = end if end is not None else start
        after = sentence[after_at:after_at + WINDOW_SIZE]
        if any(word in sentence for word in DOUBLE_NEGATIONS) or any(word in sentence for word in UNCERTAIN_WORDS):
            return rule.uncertainty_policy
        if any(word in window for word in NEGATIONS) or any(word in after for word in TRAILING_NEGATIONS):
            return rule.negated_policy
        if any(word in sentence for word in RESOLVED_WORDS):
            return rule.resolved_policy
        return "ESCALATE"

    def hits_in_message(self, message: str) -> List[Tuple[SafetyRuleDef, int, str, str]]:
        hits: List[Tuple[SafetyRuleDef, int, str, str]] = []
        for sentence in self._split(message):
            norm = compact_text(sentence)
            if not norm:
                continue
            for rule in self.rules:
                for start, end in self.alias_matches(rule, norm):
                    hits.append((rule, start, self.action_of(rule, norm, start, end), sentence))
        hits.sort(key=lambda item: item[1])
        return hits

    def evaluate(self, messages: Sequence[str]) -> SafetyView:
        messages = [m for m in (messages or []) if m is not None]
        matched: List[str] = []
        shadows: List[str] = []
        urgent = human = unsuitable = prompt = undetermined = False

        def apply(rule: SafetyRuleDef, action: str) -> None:
            nonlocal urgent, human, unsuitable, prompt, undetermined
            if rule.status == "DRAFT" and not self.enforce_draft:
                if action != "SUPPRESS" and rule.rule_id not in shadows:
                    shadows.append(rule.rule_id)
                return
            if action == "SUPPRESS":
                return
            if action == "UNDETERMINED":
                undetermined = True
            else:
                if rule.disposition == "URGENT":
                    urgent = True
                elif rule.disposition == "HUMAN":
                    human = True
                elif rule.disposition == "UNSUITABLE_FOR_ONLINE":
                    unsuitable = True
                elif rule.disposition == "PROMPT_EVALUATION_BLOCK_ONLINE":
                    prompt = True
                    unsuitable = True
                elif rule.disposition == "PROMPT_EVALUATION":
                    prompt = True
            if rule.rule_id not in matched:
                matched.append(rule.rule_id)

        per_message = [self.hits_in_message(message) for message in messages]
        for hits in per_message:
            for rule, start, action, _sentence in hits:
                apply(rule, action)
        if len(messages) >= 2 and any(word in messages[-1] for word in CURRENT_ANAPHORA):
            for hits in per_message[:-1]:
                for rule, _start, _action, _sentence in hits:
                    apply(rule, "ESCALATE")

        if urgent:
            return SafetyView(True, 3, matched, shadows, 1103)
        if human:
            return SafetyView(True, 6, matched, shadows, 1110)
        if undetermined:
            return SafetyView(True, 7, matched, shadows, 1111)
        if unsuitable or prompt:
            return SafetyView(True, 8, matched, shadows, 1112)
        return SafetyView(False, None, matched, shadows)

    @staticmethod
    def _split(text: str) -> List[str]:
        parts = [text]
        for splitter in SPLITTERS:
            nxt: List[str] = []
            for part in parts:
                nxt.extend(part.split(splitter))
            parts = nxt
        return [part for part in parts if part]


# ---------------------------------------------------------------- 加载
def load_catalog(catalog_path: pathlib.Path = CATALOG_JSON) -> Snapshot:
    data = json.loads(catalog_path.read_text(encoding="utf-8"))
    _DEPARTMENT_NAMES.clear()
    records: List[DeptRecord] = []
    for item in data.get("departments", []):
        specs = [
            Specialty(
                name=spec.get("name", ""),
                treat_desc=spec.get("treatDesc", "") or "",
                keywords=tuple(spec.get("keywords") or []),
                diseases=tuple(spec.get("diseases") or []),
            )
            for spec in item.get("specialties") or []
        ]
        routing: List[str] = []
        override: List[str] = []
        for kw in item.get("keywords") or []:
            if kw.get("kind") == "OVERRIDE":
                override.append(kw.get("text", ""))
            else:
                routing.append(kw.get("text", ""))
        _DEPARTMENT_NAMES[item["code"]] = item["name"]
        records.append(DeptRecord(
            code=item["code"], name=item["name"], enabled=bool(item.get("enabled")),
            sort=item.get("sort", 0), fallback=bool(item.get("fallback")),
            gender_scope=item.get("genderScope", 0), child_age_lt=item.get("childAgeLt"),
            treat_desc=item.get("treatDesc", "") or "", specialties=specs,
            routing_keywords=tuple(routing), override_keywords=tuple(override)))
    typos = [(t.get("from", ""), t.get("to", "")) for t in data.get("typos") or []]
    return Snapshot(records, data.get("version", ""), typos,
                    list(data.get("uncertain") or []), list(data.get("ambiguous") or []),
                    list(data.get("synonyms") or []))


def load_safety(urgent_path: pathlib.Path = URGENT_JSON, enforce_draft: bool = False) -> RuleSafety:
    data = json.loads(urgent_path.read_text(encoding="utf-8"))
    rules: List[SafetyRuleDef] = []
    for item in data.get("rules", []):
        if item.get("status") == "RETIRED":
            continue
        rules.append(SafetyRuleDef(
            rule_id=item.get("ruleId", ""), status=item.get("status", "DRAFT"),
            disposition=item.get("disposition", ""),
            negated_policy=item.get("negatedPolicy", "ESCALATE"),
            uncertainty_policy=item.get("uncertaintyPolicy", "ESCALATE"),
            resolved_policy=item.get("resolvedPolicy", "ESCALATE"),
            missing_context_policy=item.get("missingContextPolicy", "ESCALATE"),
            aliases=list(item.get("aliases") or [])))
    return RuleSafety(rules, enforce_draft=enforce_draft)


# ---------------------------------------------------------------- 全链路
class Replica:
    """一次加载，逐句复刻 N1 → N6。"""

    def __init__(self, enforce_draft: bool = False, snapshot: Optional[Snapshot] = None,
                 safety: Optional[RuleSafety] = None):
        self.snapshot = snapshot or load_catalog()
        self.safety = safety or load_safety(enforce_draft=enforce_draft)
        self.enforce_draft = enforce_draft

    def run(self, description: str, gender: Optional[int] = None, age: Optional[int] = None,
            turns: int = 1, prior_messages: Optional[Sequence[str]] = None) -> dict:
        """跑一次完整链路。多轮时安全/挂科/槽位看整段对话，含糊只看当前这句。"""
        messages = list(prior_messages or [])
        messages.append(description)
        transcript = "，".join(item for item in messages if item and item.strip())
        view = self.safety.evaluate(messages)
        canon = canon_rule(transcript or description, self.snapshot)
        decision = route(transcript or description, canon, self.snapshot, age=age, gender=gender)

        # TriageEngine 融合：不确定先走词表模型，再走 DeepSeek；两层都解不出才 FALLBACK。
        route_final = decision
        canon_final_reason = canon.reason
        canon_final_source = "RULE"
        route_fixed = decision.reason in ("UNCERTAIN_MARKER", "OVERRIDE")
        need_model = not decision.certain() or (not canon.certain() and not route_fixed)
        if need_model:
            if route_fixed and not canon.certain():
                canon_final_reason = "SKIPPED_BY_ROUTE"
            else:
                candidates = list(decision.candidates)
                if decision.certain():
                    candidates = list(dict.fromkeys(
                        spec.name
                        for department in self.snapshot.enabled
                        for spec in department.specialties
                    ))
                    if self.snapshot.fallback.name not in candidates:
                        candidates.append(self.snapshot.fallback.name)
                modeled = lexicon_interpret(
                    transcript or description, self.snapshot, age, gender, candidates)
                if modeled is None:
                    modeled = llm_interpret(
                        transcript or description, self.snapshot, age, gender, candidates)
                if modeled is not None:
                    if not canon.certain():
                        canon_final_reason = "MODEL_TERMS" if modeled.reason == "MODEL_TERM_ROUTED" else "MODEL_NONE"
                        canon_final_source = "MODEL"
                    if not decision.certain():
                        route_final = modeled
                    elif (not canon.certain()
                          and modeled.reason == "MODEL_TERM_ROUTED"
                          and modeled.advice.specialty != decision.advice.specialty):
                        route_final = modeled
                else:
                    from complaint_llm import last_status, live_enabled
                    suffix = last_status() if live_enabled() else "UNAVAILABLE"
                    if suffix not in ("TIMEOUT", "UNAVAILABLE", "INVALID_OUTPUT", "BUSY", "BREAKER_OPEN"):
                        suffix = "UNAVAILABLE"
                    if not canon.certain():
                        canon_final_reason = "MODEL_" + suffix
                        canon_final_source = "FALLBACK"
                    if not decision.certain():
                        route_final = RouteDecision(
                            _fallback_advice(self.snapshot), "UNCERTAIN", "FALLBACK",
                            "MODEL_" + suffix, list(decision.candidates))
        symptoms = extract_symptoms(transcript or description, self.snapshot)

        gender_out = gender if gender is not None else parse_gender(transcript or description)
        age_out = age if age is not None else parse_age(transcript or description)
        duration_out = parse_duration(transcript or description)
        missing = next_missing(gender_out, age_out, duration_out)

        vague = is_vague(description, self.snapshot)
        ask_again = False
        if view.stop:
            status_code = view.status_code
            node = "N1"
        elif vague and turns <= 1:
            status_code = 1
            node = "N2"
            ask_again = True
        elif missing is not None:
            status_code = 2
            node = "N3"
        else:
            status_code = 4
            node = "N6"

        return {
            "safety": {
                "stop": view.stop,
                "status_code": view.status_code,
                "status_name": STATUS_NAME.get(view.status_code) if view.status_code else "放行",
                "matched_rules": view.matched_rules,
                "shadow_rules": view.shadow_rules,
                "stop_code": view.stop_code,
            },
            "clarity": {"vague": vague, "ask_again": ask_again},
            "slots": {
                "gender": gender_out, "age": age_out, "duration": duration_out,
                "missing": missing,
                "gender_name": GENDER_NAME.get(gender_out) if gender_out else None,
            },
            "canon": {
                "terms": [term for term, _ in canon.terms],
                "term_evidence": canon.terms,
                "history_terms": canon.history_terms,
                "symptoms": [phrase for phrase, _ in canon.symptoms],
                "ambiguous_symptoms": [phrase for phrase, amb in canon.symptoms if amb],
                "shadows": canon.shadows,
                "reason": canon.reason,
                "certain": canon.certain(),
                "final_reason": canon_final_reason,
                "final_source": canon_final_source,
            },
            "symptoms": symptoms,
            "route": {
                "department": route_final.advice.department,
                "department_code": route_final.advice.department_code,
                "specialty": route_final.advice.specialty,
                "evidence": route_final.advice.evidence,
                "reason": route_final.reason,
                "source": route_final.source,
                "certainty": route_final.certainty,
                "candidates": route_final.candidates,
                "model_name": route_final.model_name,
                "prompt_version": route_final.prompt_version,
            },
            "route_rule_first_pass": {
                "reason": decision.reason,
                "source": decision.source,
                "certainty": decision.certainty,
                "specialty": decision.advice.specialty,
            },
            "status_code": status_code,
            "status_name": STATUS_NAME.get(status_code, ""),
            "node": node,
        }
