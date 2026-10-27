# -*- coding: utf-8 -*-
"""把断言不成立的行按「金标 vs 现网」去重导出。"""
import pathlib, sys, collections
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from score_triage_cases import load_rows  # noqa: E402
from gold_assert import evaluate  # noqa: E402

rows = load_rows(pathlib.Path(__file__).resolve().parent / "triage-cases.csv")
groups = collections.Counter()
example = {}
for row in rows:
    token = row.get("spec_assert", "")
    if not token or evaluate(token, row):
        continue
    key = (row["suite"], row["case_ref"], row["gold_specialty"], row["expect_specialty"],
           row["expect_route_reason_rule"], row["expect_route_reason"],
           row["expect_status_name"], row["expect_safety_status"])
    groups[key] += 1
    example.setdefault(key, row["input"])
lines = []
for key, count in sorted(groups.items(), key=lambda kv: (-kv[1], kv[0][0])):
    suite, ref, gold, actual, reason, final, status, safety = key
    lines.append(f"{suite}\t{ref}\t例子={example[key]}\t数量={count}\t金标={gold}\t现网={actual}\t"
                 f"原因={reason}/{final}\t状态={status}\t安全={safety}")
target = pathlib.Path(__file__).resolve().parent / "gaps.txt"
body = "\n".join(lines) if lines else "无（spec_assert 全部成立）"
target.write_text(body + "\n", encoding="utf-8")
print(f"差距条目 {len(lines)}，涉及行数 {sum(groups.values())}")
