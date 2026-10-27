# -*- coding: utf-8 -*-
"""生成科室种子、前端目录和 db/seed.sql。表结构在 db/schema.sql。

默认从 科室目录.json 重写 catalog-seed.json（会丢掉手工补的挂词）。
只把现有 catalog-seed.json 灌进 db/seed.sql：

    python docs/对齐/generate_department_catalog.py --sql-only
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent / "科室目录.json"
SVC = (
    ROOT
    / "consult-registration-agent"
    / "consult-registration-agent-service"
    / "src"
    / "main"
    / "resources"
)
SEED = SVC / "department" / "catalog-seed.json"
SQL = SVC / "db" / "seed.sql"
WEB_DEPTS = ROOT / "consult-registration-web" / "src" / "constants" / "departments.js"
HEAD_SEED = "consult-registration-agent/consult-registration-agent-service/src/main/resources/department/catalog-seed.json"

NAME_TO_CODE = {
    "内科": 1,
    "外科": 2,
    "男科": 3,
    "全科": 4,
    "骨科": 5,
    "妇产科": 6,
    "口腔科": 7,
    "儿科": 8,
    "耳鼻咽喉头颈外科": 9,
    "耳鼻喉科": 9,
    "中医科": 10,
    "皮肤性病科": 11,
    "感染病科": 12,
    "眼科": 13,
    "精神心理科": 14,
    "整形美容科": 15,
    "营养科": 16,
    "肿瘤科": 17,
    "生殖医学中心": 18,
    "麻醉医学科": 19,
    "睡眠医学科": 20,
    "中西医结合科": 21,
    "康复医学科": 22,
}
CODE_TO_NAME = {
    1: "内科",
    2: "外科",
    3: "男科",
    4: "全科",
    5: "骨科",
    6: "妇产科",
    7: "口腔科",
    8: "儿科",
    9: "耳鼻咽喉头颈外科",
    10: "中医科",
    11: "皮肤性病科",
    12: "感染病科",
    13: "眼科",
    14: "精神心理科",
    15: "整形美容科",
    16: "营养科",
    17: "肿瘤科",
    18: "生殖医学中心",
    19: "麻醉医学科",
    20: "睡眠医学科",
    21: "中西医结合科",
    22: "康复医学科",
}
TCM_HINT = (
    "虚", "阳", "阴", "湿", "寒", "热", "痰", "瘀", "痹", "外感", "中医",
    "疳", "癖", "痨", "疬", "疝", "疮", "疡", "带下", "精浊", "功血", "崩漏",
    "盗汗", "自汗", "肝郁", "肝经", "肺气", "心阳", "丹痧", "瘊子", "羊毛疔",
    "下痢", "热秘", "乳癖", "针灸", "艾灸", "把脉",
)
# 口语词落到本一级类下哪个二级科：命中词片段 → 二级科名片段。
ORAL_HINTS = (
    ("咳", "呼吸"),
    ("喘", "呼吸"),
    ("痰", "呼吸"),
    ("肺", "呼吸"),
    ("气管", "呼吸"),
    ("嗓", "耳鼻咽"),
    ("咽", "耳鼻咽"),
    ("喉", "耳鼻咽"),
    ("鼻", "耳鼻咽"),
    ("耳", "耳鼻咽"),
    ("打呼噜", "耳鼻咽"),
    ("喷嚏", "耳鼻咽"),
    ("胃", "消化"),
    ("腹", "消化"),
    ("肚", "消化"),
    ("泻", "消化"),
    ("吐", "消化"),
    ("拉肚", "消化"),
    ("消化", "消化"),
    ("恶心", "消化"),
    ("头", "神经"),
    ("脑", "神经"),
    ("晕", "神经"),
    ("癫痫", "神经"),
    ("骨折", "创伤骨科"),
    ("腰", "脊柱"),
    ("颈", "脊柱"),
    ("足跟", "足踝"),
    ("跟疼", "足踝"),
    ("痔", "肛肠"),
    ("阑尾", "胃肠"),
    ("甲沟", "普外"),
    ("牙", "口腔"),
    ("齿", "口腔"),
    ("月经", "妇科"),
    ("阴道", "妇科"),
    ("孕", "产科"),
    ("前列腺", "男科"),
    ("疹", "皮肤"),
    ("皮肤", "皮肤"),
    ("癣", "皮肤"),
    ("眼", "眼科"),
    ("视力", "眼科"),
)
TO_SKIN = {
    "皮疹", "湿疹", "荨麻疹", "痤疮", "脱发", "皮肤痒", "红疹",
    "皮肤科", "白癜风", "足癣",
}
TO_EYE = {
    "眼睛红", "眼睛干", "飞蚊症", "视力模糊", "眼科", "结膜炎", "麦粒肿",
}


def sql_str(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def terms_of(name: str, treat_desc: str) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for raw in [name, *(treat_desc.split())]:
        word = raw.strip()
        if len(word) < 2 or len(word) > 32 or word in seen:
            continue
        seen.add(word)
        out.append(word)
    return out


def default_specialty(specialties: list[dict], l1_name: str) -> str:
    for spec in specialties:
        if spec["name"] == l1_name:
            return spec["name"]
    return specialties[0]["name"] if specialties else l1_name


def assign_oral(word: str, specialties: list[dict], l1_name: str) -> str:
    for spec in specialties:
        parts = {spec["name"], *spec["treatDesc"].split()}
        if word in parts or spec["name"] in word:
            return spec["name"]
    best = None
    for spec in specialties:
        name = spec["name"]
        if name and name in word:
            if best is None or len(name) > len(best):
                best = name
    if best:
        return best
    for token, hint in ORAL_HINTS:
        if token not in word:
            continue
        for spec in specialties:
            if hint in spec["name"]:
                return spec["name"]
    return default_specialty(specialties, l1_name)


def head_seed() -> dict:
    raw = subprocess.check_output(["git", "show", f"HEAD:{HEAD_SEED}"], cwd=ROOT)
    return json.loads(raw.decode("utf-8"))


def dump_sql(seed: dict) -> str:
    """从已写好的 catalog-seed.json 灌 db/seed.sql，不回读 科室目录.json。"""
    departments = seed["departments"]
    lines = [
        "-- 科室目录数据。先执行 schema.sql。本文件可重复跑：先清空匹配词和二级科再灌入。",
        "-- 不改咨询会话、原话、审计、订单。执行后延时双删 consult:dept:catalog。",
        "",
        "DELETE FROM consult_department_keyword;",
        "DELETE FROM consult_department_specialty;",
        "",
    ]
    for dept in departments:
        child = "NULL" if dept.get("childAgeLt") is None else str(dept["childAgeLt"])
        lines.append(
            "INSERT IGNORE INTO consult_department "
            "(code, name, enabled, sort_no, fallback, gender_scope, child_age_lt, treat_desc, remark) VALUES "
            "({code}, {name}, 1, {sort}, {fallback}, {gender}, {child}, {treat}, {remark});".format(
                code=dept["code"],
                name=sql_str(dept["name"]),
                sort=dept["sort"],
                fallback=1 if dept.get("fallback") else 0,
                gender=dept.get("genderScope", 0),
                child=child,
                treat=sql_str(dept.get("treatDesc") or ""),
                remark=sql_str("分科目录"),
            )
        )
        lines.append(
            "UPDATE consult_department SET name = {name}, enabled = 1, sort_no = {sort}, "
            "fallback = {fallback}, gender_scope = {gender}, child_age_lt = {child}, "
            "treat_desc = {treat}, remark = {remark} WHERE code = {code} AND is_deleted = 0;".format(
                name=sql_str(dept["name"]),
                sort=dept["sort"],
                fallback=1 if dept.get("fallback") else 0,
                gender=dept.get("genderScope", 0),
                child=child,
                treat=sql_str(dept.get("treatDesc") or ""),
                remark=sql_str("分科目录"),
                code=dept["code"],
            )
        )
    spec_sql = []
    sort_no = 0
    for dept in departments:
        for spec in dept["specialties"]:
            sort_no += 1
            spec_sql.append(
                "({code}, {name}, {desc}, {sort}, {remark})".format(
                    code=dept["code"],
                    name=sql_str(spec["name"]),
                    desc=sql_str(spec.get("treatDesc") or ""),
                    sort=sort_no,
                    remark=sql_str("二级科"),
                )
            )
    lines.append("")
    lines.append("INSERT INTO consult_department_specialty (department_code, name, treat_desc, sort_no, remark) VALUES")
    lines.append(",\n".join(spec_sql) + ";")
    kw_sql = []
    for dept in departments:
        for kw in dept.get("keywords") or []:
            kind = 2 if kw.get("kind") == "OVERRIDE" else 1
            spec = "NULL" if not kw.get("specialty") else sql_str(kw["specialty"])
            kw_sql.append(
                "({code}, {word}, {spec}, {kind}, {remark})".format(
                    code=dept["code"],
                    word=sql_str(kw["text"]),
                    spec=spec,
                    kind=kind,
                    remark=sql_str("分科描述"),
                )
            )
    lines.append("")
    lines.append(
        "INSERT INTO consult_department_keyword "
        "(department_code, keyword, specialty_name, kind, remark) VALUES"
    )
    lines.append(",\n".join(kw_sql) + ";")
    lines.append("")
    for item in seed.get("typos") or []:
        lines.append(
            "INSERT IGNORE INTO consult_match_typo (from_word, to_word, remark) VALUES "
            "({frm}, {to}, {remark});".format(
                frm=sql_str(item["from"]),
                to=sql_str(item["to"]),
                remark=sql_str("匹配错字"),
            )
        )
    lines.append("")
    for marker in seed.get("uncertain") or []:
        lines.append(
            "INSERT IGNORE INTO consult_uncertain_marker (marker, remark) VALUES "
            "({marker}, {remark});".format(
                marker=sql_str(marker),
                remark=sql_str("不确定定科"),
            )
        )
    lines.append("")
    return "\n".join(lines) + "\n"


def dump_seed(seed: dict) -> str:
    lines = ["{", f'  "version": {json.dumps(seed["version"], ensure_ascii=False)},']
    lines.append('  "typos": [')
    typos = seed.get("typos") or []
    for i, item in enumerate(typos):
        comma = "," if i < len(typos) - 1 else ""
        lines.append(
            '    {{"from": {frm}, "to": {to}}}{comma}'.format(
                frm=json.dumps(item["from"], ensure_ascii=False),
                to=json.dumps(item["to"], ensure_ascii=False),
                comma=comma,
            )
        )
    lines.append("  ],")
    lines.append('  "uncertain": [')
    uncertain = seed.get("uncertain") or []
    for i, item in enumerate(uncertain):
        comma = "," if i < len(uncertain) - 1 else ""
        lines.append("    {0}{1}".format(json.dumps(item, ensure_ascii=False), comma))
    lines.append("  ],")
    lines.append('  "ambiguous": {0},'.format(json.dumps(seed.get("ambiguous") or [], ensure_ascii=False)))
    lines.append('  "synonyms": [')
    synonyms = seed.get("synonyms") or []
    for i, item in enumerate(synonyms):
        comma = "," if i < len(synonyms) - 1 else ""
        lines.append("    {0}{1}".format(json.dumps(item, ensure_ascii=False), comma))
    lines.append("  ],")
    lines.append('  "departments": [')
    depts = seed["departments"]
    for di, dept in enumerate(depts):
        lines.append("    {")
        lines.append('      "code": {0},'.format(dept["code"]))
        lines.append('      "name": {0},'.format(json.dumps(dept["name"], ensure_ascii=False)))
        lines.append('      "enabled": {0},'.format("true" if dept.get("enabled", True) else "false"))
        lines.append('      "sort": {0},'.format(dept["sort"]))
        lines.append('      "fallback": {0},'.format("true" if dept.get("fallback") else "false"))
        lines.append('      "genderScope": {0},'.format(dept.get("genderScope", 0)))
        if dept.get("childAgeLt") is not None:
            lines.append('      "childAgeLt": {0},'.format(dept["childAgeLt"]))
        if dept.get("treatDesc"):
            lines.append('      "treatDesc": {0},'.format(json.dumps(dept["treatDesc"], ensure_ascii=False)))
        specs = dept.get("specialties") or []
        lines.append('      "specialties": [')
        for si, spec in enumerate(specs):
            comma = "," if si < len(specs) - 1 else ""
            lines.append(
                "        {{\n"
                '          "name": {name},\n'
                '          "treatDesc": {desc},\n'
                '          "keywords": {kws},\n'
                '          "diseases": {dis}\n'
                "        }}{comma}".format(
                    name=json.dumps(spec["name"], ensure_ascii=False),
                    desc=json.dumps(spec.get("treatDesc") or "", ensure_ascii=False),
                    kws=json.dumps(spec.get("keywords") or [], ensure_ascii=False),
                    dis=json.dumps(spec.get("diseases") or [], ensure_ascii=False),
                    comma=comma,
                )
            )
        lines.append("      ],")
        lines.append('      "keywords": [')
        kws = dept.get("keywords") or []
        for ki, kw in enumerate(kws):
            comma = "," if ki < len(kws) - 1 else ""
            extra = ""
            if kw.get("specialty"):
                extra = ', "specialty": {0}'.format(json.dumps(kw["specialty"], ensure_ascii=False))
            lines.append(
                '        {{"text": {text}, "kind": {kind}{extra}}}{comma}'.format(
                    text=json.dumps(kw["text"], ensure_ascii=False),
                    kind=json.dumps(kw["kind"], ensure_ascii=False),
                    extra=extra,
                    comma=comma,
                )
            )
        lines.append("      ]")
        lines.append("    }" + ("," if di < len(depts) - 1 else ""))
    lines.append("  ]")
    lines.append("}")
    lines.append("")
    return "\n".join(lines)


def dump_web(departments: list[dict]) -> str:
    rows = []
    for dept in sorted(departments, key=lambda item: item["sort"]):
        rows.append(
            {
                "code": dept["code"],
                "label": dept["name"],
                "specialties": [
                    {"name": spec["name"], "treatDesc": spec["treatDesc"]}
                    for spec in dept["specialties"]
                ],
            }
        )
    return (
        "/** 一级类 + 二级挂号科。选科和展示用二级名。 */\n"
        "export const DEPARTMENT_TREE = "
        + json.dumps(rows, ensure_ascii=False, indent=2)
        + "\n"
    )


def main() -> None:
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    original = head_seed()
    original_by_code = {item["code"]: item for item in original["departments"]}

    l1_rows = []
    for sort, level1 in enumerate(source["level1"], start=1):
        name = level1["text"]
        if name not in NAME_TO_CODE:
            raise SystemExit("未映射一级科: " + name)
        code = NAME_TO_CODE[name]
        l1_rows.append(
            {
                "code": code,
                "name": CODE_TO_NAME[code],
                "sort": sort,
                "specialties": [
                    {
                        "name": item["text"],
                        "treatDesc": item["extend"],
                        "keywords": [],
                    }
                    for item in level1["options"]
                ],
            }
        )

    by_code = {row["code"]: row for row in l1_rows}
    names = [spec["name"] for row in l1_rows for spec in row["specialties"]]
    if len(names) != len(set(names)):
        raise SystemExit("二级科重名，选科值不能只用名称")

    departments = []
    for code in range(1, 23):
        src = by_code[code]
        old = original_by_code.get(code, {})
        treat_parts: list[str] = []
        seen_t: set[str] = set()
        for spec in src["specialties"]:
            for part in spec["treatDesc"].split():
                if part in seen_t:
                    continue
                seen_t.add(part)
                treat_parts.append(part)
        dept = {
            "code": code,
            "name": CODE_TO_NAME[code],
            "enabled": True,
            "sort": src["sort"],
            "fallback": code == 4,
            "genderScope": 1 if code == 3 else 2 if code == 6 else 0,
            "treatDesc": " ".join(treat_parts)[:500],
            "specialties": src["specialties"],
            "keywords": [],
        }
        if code == 8:
            dept["childAgeLt"] = old.get("childAgeLt", 14)
            dept["genderScope"] = old.get("genderScope", 0)
        departments.append(dept)

    spec_of: dict[tuple[int, str], dict] = {}
    for dept in departments:
        for spec in dept["specialties"]:
            spec_of[(dept["code"], spec["name"])] = spec

    used: set[str] = set()
    for dept in departments:
        for spec in dept["specialties"]:
            for word in terms_of(spec["name"], spec["treatDesc"]):
                if word in used:
                    continue
                if dept["code"] == 10 and word != spec["name"] and not any(h in word for h in TCM_HINT):
                    continue
                used.add(word)
                spec["keywords"].append(word)
                dept["keywords"].append(
                    {"text": word, "kind": "ROUTING", "specialty": spec["name"]}
                )

    for code, old in original_by_code.items():
        dept = next(item for item in departments if item["code"] == code)
        for kw in old.get("keywords") or []:
            text = kw["text"]
            kind = kw.get("kind", "ROUTING")
            if kind == "OVERRIDE":
                if not any(item["text"] == text for item in dept["keywords"]):
                    dept["keywords"].append({"text": text, "kind": "OVERRIDE"})
                continue
            if any(item["text"] == text for item in dept["keywords"]):
                continue
            if text not in used:
                used.add(text)
            spec_name = assign_oral(text, dept["specialties"], dept["name"])
            if text not in spec_of[(dept["code"], spec_name)]["keywords"]:
                spec_of[(dept["code"], spec_name)]["keywords"].append(text)
            dept["keywords"].append({"text": text, "kind": "ROUTING", "specialty": spec_name})

    def take_from_general(word: str, target_code: int, spec_name: str) -> None:
        general = next(item for item in departments if item["code"] == 4)
        general["keywords"] = [kw for kw in general["keywords"] if kw["text"] != word]
        for spec in general["specialties"]:
            spec["keywords"] = [item for item in spec["keywords"] if item != word]
        target = next(item for item in departments if item["code"] == target_code)
        if any(kw["text"] == word for kw in target["keywords"]):
            return
        spec_of[(target_code, spec_name)]["keywords"].append(word)
        target["keywords"].append({"text": word, "kind": "ROUTING", "specialty": spec_name})

    for word in TO_SKIN:
        take_from_general(word, 11, "皮肤科")
    for word in TO_EYE:
        take_from_general(word, 13, "眼科")

    seed = {
        "version": "2026.10.22",
        "typos": original.get("typos") or [],
        "uncertain": original.get("uncertain") or [],
        "departments": departments,
    }
    SEED.write_text(dump_seed(seed), encoding="utf-8")
    WEB_DEPTS.write_text(dump_web(departments), encoding="utf-8")

    lines = [
        "-- 科室目录数据。先执行 schema.sql。本文件可重复跑：先清空匹配词和二级科再灌入。",
        "-- 不改咨询会话、原话、审计、订单。执行后延时双删 consult:dept:catalog。",
        "",
        "DELETE FROM consult_department_keyword;",
        "DELETE FROM consult_department_specialty;",
        "",
    ]
    for dept in departments:
        child = "NULL" if dept.get("childAgeLt") is None else str(dept["childAgeLt"])
        lines.append(
            "INSERT IGNORE INTO consult_department "
            "(code, name, enabled, sort_no, fallback, gender_scope, child_age_lt, treat_desc, remark) VALUES "
            "({code}, {name}, 1, {sort}, {fallback}, {gender}, {child}, {treat}, {remark});".format(
                code=dept["code"],
                name=sql_str(dept["name"]),
                sort=dept["sort"],
                fallback=1 if dept["fallback"] else 0,
                gender=dept["genderScope"],
                child=child,
                treat=sql_str(dept.get("treatDesc") or ""),
                remark=sql_str("分科目录"),
            )
        )
        lines.append(
            "UPDATE consult_department SET name = {name}, enabled = 1, sort_no = {sort}, "
            "fallback = {fallback}, gender_scope = {gender}, child_age_lt = {child}, "
            "treat_desc = {treat}, remark = {remark} WHERE code = {code} AND is_deleted = 0;".format(
                name=sql_str(dept["name"]),
                sort=dept["sort"],
                fallback=1 if dept["fallback"] else 0,
                gender=dept["genderScope"],
                child=child,
                treat=sql_str(dept.get("treatDesc") or ""),
                remark=sql_str("分科目录"),
                code=dept["code"],
            )
        )
    spec_sql = []
    sort_no = 0
    for dept in departments:
        for spec in dept["specialties"]:
            sort_no += 1
            spec_sql.append(
                "({code}, {name}, {desc}, {sort}, {remark})".format(
                    code=dept["code"],
                    name=sql_str(spec["name"]),
                    desc=sql_str(spec["treatDesc"]),
                    sort=sort_no,
                    remark=sql_str("二级科"),
                )
            )
    lines.append("")
    lines.append("INSERT INTO consult_department_specialty (department_code, name, treat_desc, sort_no, remark) VALUES")
    lines.append(",\n".join(spec_sql) + ";")
    kw_sql = []
    for dept in departments:
        for kw in dept["keywords"]:
            kind = 2 if kw["kind"] == "OVERRIDE" else 1
            spec = "NULL" if not kw.get("specialty") else sql_str(kw["specialty"])
            kw_sql.append(
                "({code}, {word}, {spec}, {kind}, {remark})".format(
                    code=dept["code"],
                    word=sql_str(kw["text"]),
                    spec=spec,
                    kind=kind,
                    remark=sql_str("分科描述"),
                )
            )
    lines.append("")
    lines.append(
        "INSERT INTO consult_department_keyword "
        "(department_code, keyword, specialty_name, kind, remark) VALUES"
    )
    lines.append(",\n".join(kw_sql) + ";")
    lines.append("")
    for item in seed["typos"]:
        lines.append(
            "INSERT IGNORE INTO consult_match_typo (from_word, to_word, remark) VALUES "
            "({frm}, {to}, {remark});".format(
                frm=sql_str(item["from"]),
                to=sql_str(item["to"]),
                remark=sql_str("匹配错字"),
            )
        )
    lines.append("")
    for marker in seed["uncertain"]:
        lines.append(
            "INSERT IGNORE INTO consult_uncertain_marker (marker, remark) VALUES "
            "({marker}, {remark});".format(
                marker=sql_str(marker),
                remark=sql_str("不确定定科"),
            )
        )
    lines.append("")
    SQL.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print("l1", len(departments), "l2", sum(len(d["specialties"]) for d in departments))
    for dept in departments:
        print(
            dept["code"],
            dept["name"],
            "spec",
            len(dept["specialties"]),
            "kw",
            len(dept["keywords"]),
        )


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--sql-only":
        seed = json.loads(SEED.read_text(encoding="utf-8"))
        SQL.write_text(dump_sql(seed), encoding="utf-8")
        depts = seed["departments"]
        print(
            "sql-only l1",
            len(depts),
            "l2",
            sum(len(d["specialties"]) for d in depts),
            "kw",
            sum(len(d.get("keywords") or []) for d in depts),
        )
    else:
        main()
