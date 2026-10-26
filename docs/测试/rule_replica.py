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
    """复刻 ComplaintClarity.isVague。病名或已审同义命中也不含糊。"""
    if sentence is None:
        return True
    text = sentence.strip()
    if not text:
        return True
    if extract_symptoms(text):
        return False
    if canon_rule(text)["terms"]:
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
    """黄金样例看二级挂号科。走新的 N4+N6 规则层（不调模型，定不了就全科）。"""
    routed = route_full(description)
    return routed["specialty"], routed["evidence"]


HISTORY_BEFORE = re.compile(r"(既往|以前|曾经|之前|老毛病|一直有)[^，。；,;.!?]{0,6}$")
HISTORY_AFTER = re.compile(r"^[^，。；,;.!?]{0,3}(病史|多年|好几年|十几年|吃药控制)")
MIN_MODEL_LENGTH = 6
MAX_TERMS = 2


_INDEX_CACHE: dict | None = None


def _indexes() -> dict:
    global _INDEX_CACHE
    if _INDEX_CACHE is not None:
        return _INDEX_CACHE
    seed = load_department_seed()
    depts = catalog_departments()
    disease: dict[str, tuple[int, str]] = {}
    phrase_refs: dict[str, list[tuple[int, str]]] = {}
    for dept in depts:
        used: set[str] = set()
        default = _default_specialty(dept)
        for spec in dept.get("specialties") or []:
            for term in spec.get("diseases") or []:
                disease.setdefault(term, (dept["code"], spec["name"]))
            for term in spec.get("keywords") or []:
                used.add(term)
                phrase_refs.setdefault(term, [])
                ref = (dept["code"], spec["name"])
                if ref not in phrase_refs[term]:
                    phrase_refs[term].append(ref)
        fallback_ref = (dept["code"], default)
        for term in keywords_of(dept, "ROUTING"):
            if term in used:
                continue
            phrase_refs.setdefault(term, [])
            if fallback_ref not in phrase_refs[term]:
                phrase_refs[term].append(fallback_ref)
        name = dept.get("name") or ""
        if name not in used and len(name) >= 2:
            phrase_refs.setdefault(name, [])
            if fallback_ref not in phrase_refs[name]:
                phrase_refs[name].append(fallback_ref)
    _INDEX_CACHE = {
        "disease": disease,
        "phrase_refs": phrase_refs,
        "ambiguous": list(seed.get("ambiguous") or []),
        "synonyms": list(seed.get("synonyms") or []),
        "depts": depts,
        "by_code": {item["code"]: item for item in depts},
    }
    return _INDEX_CACHE


def _synonym_match(rule: dict, compact: str) -> tuple[int, int] | None:
    none = rule.get("none") or []
    if any(word and word in compact for word in none):
        return None
    groups = rule.get("all") or []
    if not groups:
        return None
    start, end = 10**9, -1
    for group in groups:
        at, word = -1, ""
        for item in group or []:
            found = compact.find(item) if item else -1
            if found >= 0 and (at < 0 or found < at):
                at, word = found, item
        if at < 0:
            return None
        if at < start:
            start, end = at, at + len(word)
    pattern = rule.get("pattern")
    if pattern:
        match = re.search(pattern, compact)
        if not match:
            return None
        start, end = match.start(), match.end()
    return start, end


def _in_history(text: str, start: int, end: int) -> bool:
    return bool(HISTORY_BEFORE.search(text[:start]) or HISTORY_AFTER.match(text[end:]))


def _longest_at(text: str, index: int, phrases: list[str]) -> str | None:
    best = None
    for phrase in phrases:
        if phrase and text.startswith(phrase, index) and (best is None or len(phrase) > len(best)):
            best = phrase
    return best


def canon_rule(description: str) -> dict:
    """复刻 ComplaintCanon.rule：病名、已审同义、症状；DRAFT 同义只进影子。"""
    idx = _indexes()
    text = match_compact(description)
    empty = {
        "terms": [],
        "history": [],
        "symptoms": [],
        "shadows": [],
        "certain": True,
        "reason": "EMPTY",
    }
    if not text:
        return empty
    if uncertain_department(description):
        return {**empty, "reason": "UNCERTAIN_MARKER"}
    phrases = list(dict.fromkeys(list(idx["disease"]) + symptom_phrases() + idx["ambiguous"]))
    current: list[dict] = []
    history: list[dict] = []
    symptoms: list[dict] = []
    seen: set[str] = set()
    i = 0
    while i < len(text):
        match = _longest_at(text, i, phrases)
        if match is None:
            i += 1
            continue
        span = (i, i + len(match))
        if match in idx["disease"]:
            item = {"term": match, "start": span[0], "end": span[1]}
            if _in_history(text, span[0], span[1]):
                if item["term"] not in [row["term"] for row in history]:
                    history.append(item)
            elif item["term"] not in [row["term"] for row in current]:
                current.append(item)
        elif match not in seen:
            seen.add(match)
            symptoms.append({
                "phrase": match,
                "ambiguous": match in idx["ambiguous"],
                "refs": idx["phrase_refs"].get(match, []),
                "start": span[0],
                "end": span[1],
            })
        i = span[1]
    shadows: list[str] = []
    literal = bool(current)
    synonym = False
    for rule in idx["synonyms"]:
        hit = _synonym_match(rule, text)
        if hit is None:
            continue
        status = (rule.get("status") or "DRAFT").upper()
        if status == "REVIEWED":
            item = {"term": rule["target"], "start": hit[0], "end": hit[1], "ruleId": rule.get("ruleId")}
            if _in_history(text, hit[0], hit[1]):
                if item["term"] not in [row["term"] for row in history]:
                    history.append(item)
            elif item["term"] not in [row["term"] for row in current]:
                current.append(item)
                synonym = True
        elif status == "DRAFT":
            shadows.append(rule.get("ruleId") or "")
    current.sort(key=lambda row: row["start"])
    terms = current[:MAX_TERMS]
    if terms:
        return {
            "terms": terms,
            "history": history,
            "symptoms": symptoms,
            "shadows": shadows,
            "certain": True,
            "reason": "DISEASE_LITERAL" if literal else "SYNONYM",
        }
    clear = any((not hit["ambiguous"]) and hit["refs"] for hit in symptoms)
    ambiguous = any(hit["ambiguous"] for hit in symptoms)
    if clear:
        reason, certain = "SYMPTOM_ONLY", True
    elif ambiguous:
        reason, certain = "AMBIGUOUS_ONLY", False
    elif len(text) >= MIN_MODEL_LENGTH or any(("a" <= ch <= "z") or ("A" <= ch <= "Z") for ch in text):
        # 拼音短句（kesou、头tong）仍交模型，与 ComplaintCanon.hasLatinLetter 一致
        reason, certain = "NO_HIT", False
    else:
        reason, certain = "TOO_SHORT", True
    return {
        "terms": [],
        "history": history,
        "symptoms": symptoms,
        "shadows": shadows,
        "certain": certain,
        "reason": reason,
    }


def _skip_gender(dept: dict | None, gender: str | None) -> bool:
    if dept is None or gender is None:
        return False
    if gender == "女" and dept.get("genderScope") == 1:
        return True
    if gender == "男" and dept.get("genderScope") == 2:
        return True
    return False


def route_full(description: str, canon: dict | None = None) -> dict:
    """复刻 DepartmentRouter.route：规则定不了时科室落全科（评测不调模型）。"""
    idx = _indexes()
    depts = idx["depts"]
    by_code = idx["by_code"]
    fallback, fallback_spec = general_label(), "全科"
    if canon is None:
        canon = canon_rule(description)
    if description is None or not str(description).strip() or uncertain_department(description):
        return {"l1": fallback, "specialty": fallback_spec, "evidence": None, "certain": True, "reason": "UNCERTAIN_MARKER"}
    haystack = match_compact(description)
    gender = gender_of(haystack)
    age = age_of(description) or age_of(haystack)
    terms: list[dict] = []
    term_refs: list[tuple[int, str]] = []
    for term in canon.get("terms") or []:
        ref = idx["disease"].get(term["term"])
        if ref is None or _skip_gender(by_code.get(ref[0]), gender):
            continue
        terms.append(term)
        term_refs.append(ref)
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
        inner_spec, inner_ev = None, None
        for i, ref in enumerate(term_refs):
            if ref[0] == parent["code"]:
                inner_spec, inner_ev = ref[1], terms[i]["term"]
                break
        if inner_spec is None:
            inner = _first_routing(haystack, depts, parent, None)
            if inner is not None:
                return {"l1": inner[0], "specialty": inner[1], "evidence": inner[3], "certain": True, "reason": "OVERRIDE"}
            return {"l1": override_best[2], "specialty": override_best[3], "evidence": override_best[1], "certain": True, "reason": "OVERRIDE"}
        return {"l1": parent["name"], "specialty": inner_spec, "evidence": inner_ev, "certain": True, "reason": "OVERRIDE"}
    pediatric = pediatric_dept()
    child = False
    child_marker = None
    if pediatric is not None:
        child_hit = _first_hit(haystack, keywords_of(pediatric, "OVERRIDE"))
        child_marker = child_hit[1] if child_hit else None
        child = child_hit is not None or (age is not None and age < int(pediatric["childAgeLt"]))
    if child and pediatric is not None:
        routing = _first_routing(haystack, depts, pediatric, gender)
        if routing is not None:
            return {"l1": routing[0], "specialty": routing[1], "evidence": routing[3], "certain": True, "reason": "PEDIATRIC"}
        if term_refs:
            spec = _map_pediatric(pediatric, term_refs[0][1])
            return {"l1": pediatric["name"], "specialty": spec, "evidence": term_refs[0][1], "certain": True, "reason": "PEDIATRIC"}
        adult = _first_routing(haystack, depts, None, gender)
        if adult is not None:
            return {"l1": pediatric["name"], "specialty": _map_pediatric(pediatric, adult[1]), "evidence": adult[3], "certain": True, "reason": "PEDIATRIC"}
        evidence = child_marker or "儿童"
        return {"l1": pediatric["name"], "specialty": _default_specialty(pediatric), "evidence": evidence, "certain": True, "reason": "PEDIATRIC"}
    clear: list[dict] = []
    clear_refs: list[tuple[int, str]] = []
    for hit in canon.get("symptoms") or []:
        if hit.get("ambiguous"):
            continue
        for ref in hit.get("refs") or []:
            if not _skip_gender(by_code.get(ref[0]), gender):
                clear.append(hit)
                clear_refs.append(ref)
                break
    if term_refs:
        primary = term_refs[0]
        specs = list(dict.fromkeys(ref[1] for ref in term_refs))
        only_one = len(specs) == 1
        for ref in clear_refs:
            if ref[0] != primary[0]:
                if ref[1] not in specs:
                    specs.append(ref[1])
                only_one = False
        parent = by_code.get(primary[0])
        if only_one and parent is not None:
            return {"l1": parent["name"], "specialty": primary[1], "evidence": terms[0]["term"], "certain": True, "reason": "CANON_TERM"}
        return {"l1": fallback, "specialty": fallback_spec, "evidence": None, "certain": False, "reason": "CONFLICT"}
    if not clear:
        if not canon.get("certain"):
            reason = "AMBIGUOUS_ONLY" if canon.get("reason") == "AMBIGUOUS_ONLY" else "NO_HIT"
            return {"l1": fallback, "specialty": fallback_spec, "evidence": None, "certain": False, "reason": reason}
        return {"l1": fallback, "specialty": fallback_spec, "evidence": None, "certain": True, "reason": "NO_HIT_SHORT"}
    specs = list(dict.fromkeys(ref[1] for ref in clear_refs))
    level_one = {ref[0] for ref in clear_refs}
    parent = by_code.get(clear_refs[0][0])
    l1 = parent["name"] if parent else fallback
    if len(specs) == 1:
        return {"l1": l1, "specialty": specs[0], "evidence": clear[0]["phrase"], "certain": True, "reason": "SINGLE_SPECIALTY"}
    if len(level_one) == 1:
        return {"l1": l1, "specialty": clear_refs[0][1], "evidence": clear[0]["phrase"], "certain": True, "reason": "SAME_L1_EARLIEST"}
    return {"l1": fallback, "specialty": fallback_spec, "evidence": None, "certain": False, "reason": "CONFLICT"}


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
    canon = canon_rule(text)
    routed = route_full(text, canon)
    return {
        "status": status,
        "urgent": urgent,
        "human": human,
        "department": routed["l1"],
        "specialty": routed["specialty"],
        "evidence": routed["evidence"],
        "matched": list(dict.fromkeys(matched)),
        "shadows": list(dict.fromkeys(shadows)),
        "symptoms": extract_symptoms(text),
        "canon_terms": [item["term"] for item in canon["terms"]],
        "canon_reason": canon["reason"],
        "canon_shadows": canon["shadows"],
        "route_reason": routed["reason"],
        "route_certain": routed["certain"],
    }
