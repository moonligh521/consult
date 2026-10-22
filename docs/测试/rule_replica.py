# -*- coding: utf-8 -*-
"""把 Java 侧的科室路由、症状抽取、含糊判断、急症目录复刻到 Python。

单一事实来源：科室关键词、错别字、不确定标记、症状短语都从 catalog-seed.json 读，
含糊正则仍从 ComplaintClarity.java 解析，急症规则从 urgent-catalog.json 读取。
生成脚本和评测脚本共用本模块。
"""

from __future__ import annotations

import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SVC_MAIN = ROOT / "consult-registration-agent" / "consult-registration-agent-service" / "src" / "main"
JAVA = SVC_MAIN / "java" / "com" / "moonligh" / "consult" / "agent"
API_JAVA = (
    ROOT / "consult-registration-agent" / "consult-registration-agent-api"
    / "src" / "main" / "java" / "com" / "moonligh" / "consult" / "agent" / "api"
)

DEPARTMENT_JAVA = API_JAVA / "enums" / "Department.java"
CLARITY_JAVA = JAVA / "domain" / "ComplaintClarity.java"
CATALOG_JSON = SVC_MAIN / "resources" / "safety" / "urgent-catalog.json"
DEPT_SEED_JSON = SVC_MAIN / "resources" / "department" / "catalog-seed.json"
SAFETY_PHRASES = ("呼吸困难", "胸痛")

# 状态文案，和 ConsultationStatus 枚举一致。
STATUS_URGENT = "急症(3)"
STATUS_HUMAN = "转人工(6)"
STATUS_FALLBACK = "安全降级(7)"
STATUS_UNSUITABLE = "渠道不适用(8)"
STATUS_NEED_DESC = "待描述(1)"
STATUS_PLAIN = "非急症"

# 句子切分、否定/疑问判定，沿用 score_urgent_zh_cases.py 已验证的口径。
SPLITTERS = ["但是", "不过", "可是", "然而", "，", "。", "；", "！", "？", ",", ".", ";", "!", "?", "\n"]
DOUBLE_NEGATIONS = ["不是没有", "没有不", "并非没有"]
UNCERTAIN_WORDS = ["是不是", "会不会", "好像", "可能", "不确定", "说不清"]
NEGATIONS = ["没有", "并非", "不是", "否认", "没", "无", "不", "未"]
RESOLVED_WORDS = ["已经好了", "缓解了", "不疼了", "消失了", "现在没事"]

AGE_RE = re.compile(r"(\d{1,3})岁")
DURATION_RE = re.compile(r"(\d+|[一二三四五六七八九十两半]+)(小时|天|周|个月|月|年)")


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def department_labels() -> dict[str, tuple[int, str]]:
    """从 Department.java 解析 枚举名 -> (code, 中文名)。"""
    text = _read(DEPARTMENT_JAVA)
    out: dict[str, tuple[int, str]] = {}
    for name, code, label in re.findall(r'([A-Z][A-Z_]+)\((\d+),\s*"([^"]+)"\)', text):
        out[name] = (int(code), label)
    return out


def load_department_seed() -> dict:
    return json.loads(DEPT_SEED_JSON.read_text(encoding="utf-8"))


def catalog_departments() -> list[dict]:
    return [item for item in load_department_seed().get("departments") or [] if item.get("enabled", True)]


def general_label() -> str:
    for item in catalog_departments():
        if item.get("fallback"):
            if item.get("name") != "全科":
                raise ValueError("兜底科必须是全科，实际是 " + str(item.get("name")))
            return item["name"]
    raise ValueError("科室目录没有兜底全科")


def pediatric_dept() -> dict | None:
    for item in catalog_departments():
        if item.get("childAgeLt") is not None:
            return item
    return None


def pediatric_age_limit() -> int:
    dept = pediatric_dept()
    return int(dept["childAgeLt"]) if dept and dept.get("childAgeLt") else 14


def child_markers() -> list[str]:
    dept = pediatric_dept()
    if not dept:
        return []
    return [kw["text"] for kw in dept.get("keywords") or [] if kw.get("kind") == "OVERRIDE"]


def keywords_of(dept: dict, kind: str) -> list[str]:
    return [kw["text"] for kw in dept.get("keywords") or [] if kw.get("kind") == kind]


def department_groups() -> list[tuple[str, list[str]]]:
    """从 catalog-seed.json 解析已开通科室的 ROUTING 词。"""
    return [(item["name"], keywords_of(item, "ROUTING")) for item in catalog_departments()]


def symptom_phrases() -> list[str]:
    """已开通科 ROUTING 词，另补急症用的呼吸困难/胸痛。OVERRIDE 不算症状。"""
    phrases: list[str] = []
    seen: set[str] = set()
    for _name, keywords in department_groups():
        for keyword in keywords:
            if keyword in seen:
                continue
            seen.add(keyword)
            phrases.append(keyword)
    for extra in SAFETY_PHRASES:
        if extra in seen:
            continue
        seen.add(extra)
        phrases.append(extra)
    return phrases


def vague_pattern() -> re.Pattern[str]:
    """从 ComplaintClarity.java 解析 VAGUE 正则。"""
    text = _read(CLARITY_JAVA)
    match = re.search(r'VAGUE\s*=\s*Pattern\.compile\(\s*"(.*?)"\s*\)', text, re.S)
    if not match:
        return re.compile(r"(?!x)x")
    return re.compile(match.group(1))


def nfkc_compact(text: str) -> str:
    nfkc = unicodedata.normalize("NFKC", text or "")
    return "".join(ch.lower() for ch in nfkc if not ch.isspace())


def spoken_typos() -> list[tuple[str, str]]:
    """从 catalog-seed.json 读错词→正词。"""
    return [(item["from"], item["to"]) for item in load_department_seed().get("typos") or []]


def uncertain_markers() -> list[str]:
    """从 catalog-seed.json 读不确定定科标记。"""
    return list(load_department_seed().get("uncertain") or [])


def match_compact(text: str) -> str:
    """匹配用：去空格后再纠错别字。急症规则不要走这条。"""
    compact = nfkc_compact(text)
    for bad, good in spoken_typos():
        compact = compact.replace(bad, good)
    return compact


def uncertain_department(text: str) -> bool:
    compact = nfkc_compact(text)
    return any(marker in compact for marker in uncertain_markers())


def split_sentences(text: str) -> list[str]:
    out: list[str] = []
    cursor = 0
    while cursor < len(text):
        nxt = len(text)
        skip = 0
        for splitter in SPLITTERS:
            found = text.find(splitter, cursor)
            if 0 <= found < nxt:
                nxt = found
                skip = len(splitter)
        if nxt > cursor:
            out.append(text[cursor:nxt])
        cursor = min(len(text), nxt + skip)
    return out or [text]


def contains_any(text: str, needles: list[str]) -> bool:
    return any(needle in text for needle in needles)


def alias_matches(sentence: str, aliases: list[str]) -> list[tuple[int, int, str]]:
    taken = [False] * len(sentence)
    hits: list[tuple[int, int, str]] = []
    for alias in sorted(aliases, key=len, reverse=True):
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
    window = sentence[max(0, start - 6) : start]
    if contains_any(sentence, DOUBLE_NEGATIONS) or contains_any(sentence, UNCERTAIN_WORDS):
        return rule["uncertaintyPolicy"]
    if contains_any(window, NEGATIONS):
        return rule["negatedPolicy"]
    if contains_any(sentence, RESOLVED_WORDS):
        return rule["resolvedPolicy"]
    return "ESCALATE"


def load_rules() -> list[dict]:
    catalog = json.loads(CATALOG_JSON.read_text(encoding="utf-8"))
    return catalog["rules"]


def catalog_version() -> str:
    catalog = json.loads(CATALOG_JSON.read_text(encoding="utf-8"))
    return str(catalog.get("catalogVersion"))


def extract_symptoms(description: str) -> list[str]:
    """复刻 SymptomExtractor：去空格后同一位置取最长短语，不重复。"""
    phrases = symptom_phrases()
    found: list[str] = []
    text = match_compact(description)
    if not text:
        return found
    index = 0
    while index < len(text):
        best = None
        for phrase in phrases:
            if text.startswith(phrase, index):
                if best is None or len(phrase) > len(best):
                    best = phrase
        if best is None:
            index += 1
            continue
        if best not in found:
            found.append(best)
        index += len(best)
    return found


def gender_of(text: str) -> str | None:
    """复刻 SlotParser.gender：同时出现男/女时取先出现的。"""
    male_at = text.find("男")
    female_at = text.find("女")
    if male_at < 0 and female_at < 0:
        return None
    if male_at < 0:
        return "女"
    if female_at < 0:
        return "男"
    return "男" if male_at < female_at else "女"


def age_of(text: str) -> int | None:
    match = AGE_RE.search(text or "")
    if not match:
        return None
    value = int(match.group(1))
    return value if 1 <= value <= 120 else None


def duration_of(text: str) -> str | None:
    match = DURATION_RE.search(text or "")
    return match.group() if match else None


def is_vague(sentence: str) -> bool:
    """复刻 ComplaintClarity.isVague。"""
    if sentence is None:
        return True
    text = sentence.strip()
    if not text:
        return True
    if extract_symptoms(text):
        return False
    if duration_of(text) is not None:
        return False
    if gender_of(text) is not None or age_of(text) is not None:
        return False
    if vague_pattern().search(text):
        return len(text) <= 16
    return len(text) < 6


def _first_hit(haystack: str, keywords: list[str]) -> tuple[int, str] | None:
    best: tuple[int, str] | None = None
    for keyword in keywords:
        at = haystack.find(keyword)
        if at < 0:
            continue
        if best is None or at < best[0] or (at == best[0] and len(keyword) > len(best[1])):
            best = (at, keyword)
    return best


def _default_specialty(dept: dict) -> str:
    for spec in dept.get("specialties") or []:
        if spec.get("name") == dept["name"]:
            return spec["name"]
    specs = dept.get("specialties") or []
    return specs[0]["name"] if specs else dept["name"]


def _map_pediatric(pediatric: dict, adult_specialty: str) -> str:
    names = [spec["name"] for spec in pediatric.get("specialties") or []]
    if adult_specialty in names:
        return adult_specialty
    best = _default_specialty(pediatric)
    best_len = 0
    for spec in pediatric.get("specialties") or []:
        stem = spec["name"].replace("小儿", "")
        if stem.endswith("科") and len(stem) > 2:
            stem = stem[:-1]
        if len(stem) < 2:
            continue
        if adult_specialty and stem in adult_specialty and len(stem) > best_len:
            best = spec["name"]
            best_len = len(stem)
    return best


def _first_routing(
    haystack: str,
    depts: list[dict],
    only: dict | None,
    gender: str | None,
) -> tuple[str, str, int, str] | None:
    best: tuple[str, str, int, str] | None = None
    for dept in depts:
        if only is not None and dept["code"] != only["code"]:
            continue
        if only is None and gender == "女" and dept.get("genderScope") == 1:
            continue
        if only is None and gender == "男" and dept.get("genderScope") == 2:
            continue
        used: set[str] = set()
        for spec in dept.get("specialties") or []:
            for keyword in spec.get("keywords") or []:
                used.add(keyword)
                at = haystack.find(keyword)
                if at < 0:
                    continue
                if best is None or at < best[2] or (at == best[2] and len(keyword) > len(best[3])):
                    best = (dept["name"], spec["name"], at, keyword)
        fallback_spec = _default_specialty(dept)
        for keyword in keywords_of(dept, "ROUTING"):
            if keyword in used:
                continue
            at = haystack.find(keyword)
            if at < 0:
                continue
            if best is None or at < best[2] or (at == best[2] and len(keyword) > len(best[3])):
                best = (dept["name"], fallback_spec, at, keyword)
    return best


def advise_full(description: str) -> tuple[str, str, str | None]:
    """复刻 DepartmentRouter.advise：一级类、二级挂号科、依据。"""
    fallback = general_label()
    fallback_spec = "全科"
    if description is None or not description.strip():
        return fallback, fallback_spec, None
    if uncertain_department(description):
        return fallback, fallback_spec, None
    haystack = match_compact(description)
    depts = catalog_departments()
    override_best: tuple[int, str, str, str] | None = None
    for dept in depts:
        if dept.get("childAgeLt") is not None:
            continue
        hit = _first_hit(haystack, keywords_of(dept, "OVERRIDE"))
        if hit is None:
            continue
        if override_best is None or hit[0] < override_best[0] or (
            hit[0] == override_best[0] and len(hit[1]) > len(override_best[1])
        ):
            override_best = (hit[0], hit[1], dept["name"], _default_specialty(dept))
    if override_best is not None:
        parent = next(item for item in depts if item["name"] == override_best[2])
        inner = _first_routing(haystack, depts, parent, None)
        if inner is not None:
            return inner[0], inner[1], inner[3]
        return override_best[2], override_best[3], override_best[1]
    age = age_of(description) or age_of(haystack)
    gender = gender_of(haystack)
    pediatric = pediatric_dept()
    if pediatric is not None:
        child = _first_hit(haystack, keywords_of(pediatric, "OVERRIDE"))
        child_hit = child is not None or (age is not None and age < int(pediatric["childAgeLt"]))
        if child_hit:
            routing = _first_routing(haystack, depts, pediatric, gender)
            if routing is not None:
                return routing[0], routing[1], routing[3]
            adult = _first_routing(haystack, depts, None, gender)
            if adult is not None:
                return pediatric["name"], _map_pediatric(pediatric, adult[1]), adult[3]
            if child is not None:
                return pediatric["name"], _default_specialty(pediatric), child[1]
            return pediatric["name"], _default_specialty(pediatric), "儿童"
    best = _first_routing(haystack, depts, None, gender)
    if best is None:
        return fallback, fallback_spec, None
    return best[0], best[1], best[3]


def advise(description: str) -> tuple[str, str | None]:
    """黄金样例看二级挂号科。"""
    _l1, specialty, evidence = advise_full(description)
    return specialty, evidence


def evaluate(text: str, rules: list[dict] | None = None) -> dict:
    """对一句话算出：状态、是否 120、科室、依据、影子规则、症状标签。"""
    rules = rules if rules is not None else load_rules()
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
                disposition = rule["disposition"]
                if disposition == "URGENT":
                    urgent = True
                elif disposition == "HUMAN":
                    human = True
                elif disposition == "UNSUITABLE_FOR_ONLINE":
                    unsuitable = True
    if urgent:
        status = STATUS_URGENT
    elif human:
        status = STATUS_HUMAN
    elif undetermined:
        status = STATUS_FALLBACK
    elif unsuitable:
        status = STATUS_UNSUITABLE
    elif is_vague(text):
        status = STATUS_NEED_DESC
    else:
        status = STATUS_PLAIN
    l1, specialty, evidence = advise_full(text)
    return {
        "status": status,
        "urgent": urgent,
        "human": human,
        "department": l1,
        "specialty": specialty,
        "evidence": evidence,
        "matched": list(dict.fromkeys(matched)),
        "shadows": list(dict.fromkeys(shadows)),
        "symptoms": extract_symptoms(text),
    }
