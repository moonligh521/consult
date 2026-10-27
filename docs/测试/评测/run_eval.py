# -*- coding: utf-8 -*-
"""导诊评测总入口。不引入 OpenJudge、Claw-Eval、A.I.G 的运行时。

用法：
    python docs/测试/评测/run_eval.py
    python docs/测试/评测/run_eval.py --only behavior
    python docs/测试/评测/run_eval.py --only quality,adversarial,fault
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
JAVA_EVAL = REPO / "consult-registration-agent" / "consult-registration-agent-service" / "target" / "eval"
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

from gate import write_gate  # noqa: E402
from run_behavior import behavior, clinical  # noqa: E402
from run_quality import quality  # noqa: E402
from run_reliability import reliability  # noqa: E402

ALL_LINES = ("behavior", "clinical", "quality", "reliability", "adversarial", "fault")
JAVA_SUITES = {
    "behavior": "OrderStopEvalTest,SafetyInvariantsTest",
    "quality": "HardBanEvalTest",
    "adversarial": "AdversarialEvalTest",
    "fault": "FaultEvalTest",
}


def _na(line: str, detail: str) -> dict:
    return {"line": line, "result": "na", "detail": detail}


def _maven_env() -> dict:
    """Maven 要 JDK 21。当前 JAVA_HOME 不够时，用用户目录里已解压的 21。"""
    env = os.environ.copy()
    home = Path(env.get("JAVA_HOME", ""))
    java = home / "bin" / "java.exe"
    if java.exists() and "21" in home.name:
        return env
    jdks = Path.home() / ".jdks"
    if jdks.is_dir():
        for child in sorted(jdks.iterdir(), reverse=True):
            if child.is_dir() and "21" in child.name and (child / "bin" / "java.exe").exists():
                env["JAVA_HOME"] = str(child)
                env["PATH"] = str(child / "bin") + os.pathsep + env.get("PATH", "")
                return env
    return env


def _java(tests: str) -> tuple[int, str]:
    env = _maven_env()
    mvn = shutil.which("mvn", path=env.get("PATH"))
    if not mvn:
        return 1, "找不到 mvn"
    proc = subprocess.run(
        [
            mvn,
            "-pl",
            "consult-registration-agent/consult-registration-agent-service",
            "-am",
            f"-Dtest={tests}",
            "-Dsurefire.failIfNoSpecifiedTests=false",
            "test",
        ],
        cwd=REPO,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        env=env,
    )
    tail = (proc.stdout or "")[-2000:] + (proc.stderr or "")[-1000:]
    return proc.returncode, tail


def _from_java(suite: str, line: str) -> dict:
    path = JAVA_EVAL / f"{suite}.json"
    if not path.exists():
        return {"line": line, "result": "fail", "detail": f"没有 {suite}.json"}
    rows = json.loads(path.read_text(encoding="utf-8"))
    bad = [f"{row.get('case_id')} {row.get('detail')}" for row in rows if not row.get("pass")]
    if bad:
        return {"line": line, "result": "fail", "detail": "；".join(bad[:8])}
    return {"line": line, "result": "pass", "detail": ""}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", default="behavior,quality,adversarial,fault")
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--eval-id", default="eval-local")
    args = parser.parse_args()
    selected = [part.strip() for part in args.only.split(",") if part.strip()]
    if args.live and "reliability" not in selected:
        selected.append("reliability")
    unknown = [part for part in selected if part not in ALL_LINES]
    if unknown:
        print("未知计分线: " + ",".join(unknown))
        return 1

    rows = []
    if "behavior" in selected or "clinical" in selected:
        java_code, java_tail = _java(JAVA_SUITES["behavior"])
        if "behavior" in selected:
            replay = behavior()
            if java_code != 0:
                rows.append({"line": "behavior", "result": "fail", "detail": "不变量或下单断言未过"})
                (REPO / "target" / "eval").mkdir(parents=True, exist_ok=True)
                (REPO / "target" / "eval" / "maven-behavior.log").write_text(java_tail, encoding="utf-8")
            elif replay["result"] != "pass":
                rows.append(replay)
            else:
                order = _from_java("orders", "behavior")
                rows.append(order if order["result"] == "pass" else order)
        if "clinical" in selected:
            rows.append(clinical())

    java_lines = [line for line in ("quality", "adversarial", "fault") if line in selected]
    if java_lines:
        tests = ",".join(JAVA_SUITES[line] for line in java_lines)
        code, tail = _java(tests)
        (REPO / "target" / "eval").mkdir(parents=True, exist_ok=True)
        (REPO / "target" / "eval" / "maven-eval.log").write_text(tail, encoding="utf-8")
        for line in java_lines:
            if line == "quality":
                if code != 0 and not (JAVA_EVAL / "quality.json").exists():
                    rows.append({"line": "quality", "result": "fail", "detail": "HardBanEvalTest 未通过"})
                else:
                    rows.append(quality())
            else:
                if code != 0 and not (JAVA_EVAL / f"{line}.json").exists():
                    rows.append({"line": line, "result": "fail", "detail": f"{line} 测试未通过"})
                else:
                    rows.append(_from_java(line if line != "adversarial" else "adversarial", line))

    if "reliability" in selected:
        rows.append(reliability(args.live))

    for line in ALL_LINES:
        if line not in selected and not any(row["line"] == line for row in rows):
            if line == "clinical":
                rows.append(clinical())
            elif line == "reliability":
                rows.append(reliability(False))
            else:
                rows.append(_na(line, "本次 --only 未包含"))
    order = {name: index for index, name in enumerate(ALL_LINES)}
    rows.sort(key=lambda row: order[row["line"]])
    code = write_gate(REPO, args.eval_id, rows)
    print((REPO / "target" / "eval" / "report.md").read_text(encoding="utf-8"))
    return code


if __name__ == "__main__":
    sys.exit(main())
